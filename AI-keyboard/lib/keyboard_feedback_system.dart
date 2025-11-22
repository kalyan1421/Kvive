// keyboard_feedback_system.dart
// Advanced Visual and Tactile Feedback System for AI Keyboard
// Implements AnimationController, haptic feedback, particle effects, and sound feedback

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:audioplayers/audioplayers.dart';
import 'dart:math' as math;
import 'dart:async';

/// Feedback intensity levels
enum FeedbackIntensity { off, light, medium, strong }

/// Key animation specifications
class KeyAnimationSpecs {
  static const Duration pressDuration = Duration(milliseconds: 100);
  static const Duration releaseDuration = Duration(milliseconds: 200);
  static const Duration longPressPulseDuration = Duration(milliseconds: 500);
  static const double pressScale = 0.95;
  static const double pressBrightness = 1.2;
  static const double normalScale = 1.0;
  static const double normalBrightness = 1.0;
}

/// Particle effect configuration
class ParticleConfig {
  final int particleCount;
  final Duration lifetime;
  final double maxVelocity;
  final List<Color> colors;

  const ParticleConfig({
    this.particleCount = 15,
    this.lifetime = const Duration(milliseconds: 800),
    this.maxVelocity = 100.0,
    this.colors = const [
      Colors.blue,
      Colors.cyan,
      Colors.lightBlue,
      Colors.white,
    ],
  });
}

/// Individual particle for effects
class Particle {
  Offset position;
  Offset velocity;
  double life;
  double maxLife;
  Color color;
  double size;

  Particle({
    required this.position,
    required this.velocity,
    required this.life,
    required this.maxLife,
    required this.color,
    required this.size,
  });

  void update(double deltaTime) {
    position += velocity * deltaTime;
    life -= deltaTime;
    
    // Apply gravity
    velocity = velocity + const Offset(0, 50) * deltaTime;
    
    // Fade out over time
    final alpha = (life / maxLife).clamp(0.0, 1.0);
    color = color.withOpacity(alpha);
  }

  bool get isDead => life <= 0;
}

/// Ripple effect for touch feedback
class RippleEffect {
  Offset center;
  double radius;
  double maxRadius;
  double opacity;
  Color color;
  bool isActive;

  RippleEffect({
    required this.center,
    this.radius = 0.0,
    this.maxRadius = 100.0,
    this.opacity = 0.3,
    this.color = Colors.blue,
    this.isActive = true,
  });

  void update(double deltaTime) {
    if (!isActive) return;
    
    radius += 150 * deltaTime; // Expand speed
    opacity = ((maxRadius - radius) / maxRadius).clamp(0.0, 0.3);
    
    if (radius >= maxRadius) {
      isActive = false;
    }
  }
}

/// Advanced keyboard feedback system
class KeyboardFeedbackSystem {
  // SoundPool-backed audio players cached per effect (preloaded + low latency)
  static final Map<String, AudioPlayer> _soundPlayers = {};
  
  // Feedback settings
  static FeedbackIntensity _hapticIntensity = FeedbackIntensity.medium;
  static FeedbackIntensity _soundIntensity = FeedbackIntensity.off;
  static FeedbackIntensity _visualIntensity = FeedbackIntensity.off;
  static double _soundVolume = 0.0;
  static bool _soundEnabled = false;
  
  // Animation controllers cache
  static final Map<String, AnimationController> _animationControllers = {};
  static final Map<String, Animation<double>> _scaleAnimations = {};
  static final Map<String, Animation<double>> _brightnessAnimations = {};
  
  // Particle system
  static final List<Particle> _particles = [];
  static final List<RippleEffect> _ripples = [];
  static Timer? _particleUpdateTimer;
  
  /// Initialize the feedback system
  static void initialize() {
    // Start particle system update loop
    _particleUpdateTimer = Timer.periodic(
      const Duration(milliseconds: 16), // ~60 FPS
      (timer) => _updateParticleSystem(),
    );
    
    // Preload sound effects
    _preloadSounds();
  }
  
  /// Dispose resources
  static void dispose() {
    _particleUpdateTimer?.cancel();
    for (final controller in _animationControllers.values) {
      controller.dispose();
    }
    _animationControllers.clear();
    for (final player in _soundPlayers.values) {
      player.dispose();
    }
    _soundPlayers.clear();
  }
  
  /// Update feedback intensity settings
  static void updateSettings({
    FeedbackIntensity? haptic,
    FeedbackIntensity? sound,
    FeedbackIntensity? visual,
    double? volume,
  }) {
    if (haptic != null) _hapticIntensity = haptic;
    if (sound != null) _soundIntensity = sound;
    if (visual != null) _visualIntensity = visual;
    if (volume != null) _soundVolume = volume.clamp(0.0, 1.0);
  }
  
  /// Get or create animation controller for a key
  static AnimationController getAnimationController(
    String keyId, 
    TickerProvider vsync
  ) {
    if (!_animationControllers.containsKey(keyId)) {
      final controller = AnimationController(
        duration: KeyAnimationSpecs.releaseDuration,
        vsync: vsync,
      );
      
      _animationControllers[keyId] = controller;
      
      // Create scale animation with spring curve
      _scaleAnimations[keyId] = Tween<double>(
        begin: KeyAnimationSpecs.normalScale,
        end: KeyAnimationSpecs.pressScale,
      ).animate(CurvedAnimation(
        parent: controller,
        curve: Curves.elasticOut,
        reverseCurve: Curves.elasticIn,
      ));
      
      // Create brightness animation
      _brightnessAnimations[keyId] = Tween<double>(
        begin: KeyAnimationSpecs.normalBrightness,
        end: KeyAnimationSpecs.pressBrightness,
      ).animate(CurvedAnimation(
        parent: controller,
        curve: Curves.easeInOut,
      ));
    }
    
    return _animationControllers[keyId]!;
  }
  
  /// Get scale animation for a key
  static Animation<double>? getScaleAnimation(String keyId) {
    return _scaleAnimations[keyId];
  }
  
  /// Get brightness animation for a key
  static Animation<double>? getBrightnessAnimation(String keyId) {
    return _brightnessAnimations[keyId];
  }
  
  /// Trigger key press feedback
  static Future<void> onKeyPress({
    required String keyId,
    required Offset touchPoint,
    bool isSpecialKey = false,
    bool isSpaceBar = false,
    bool isEnterKey = false,
  }) async {
    // Haptic feedback
    await _triggerHapticFeedback(isSpecialKey: isSpecialKey);
    
    // Sound feedback
    await _triggerSoundFeedback(
      isSpecialKey: isSpecialKey,
      isSpaceBar: isSpaceBar,
      isEnterKey: isEnterKey,
    );
    
    // Visual feedback
    _triggerVisualFeedback(
      keyId: keyId,
      touchPoint: touchPoint,
      isSpecialKey: isSpecialKey,
      isSpaceBar: isSpaceBar,
      isEnterKey: isEnterKey,
    );
  }
  
  /// Trigger key release feedback
  static Future<void> onKeyRelease({
    required String keyId,
    bool isSpaceBar = false,
    bool isEnterKey = false,
  }) async {
    final controller = _animationControllers[keyId];
    if (controller != null) {
      // Spring bounce animation for space bar and enter key
      if (isSpaceBar || isEnterKey) {
        await controller.reverse();
        await controller.forward();
        await controller.reverse();
      } else {
        await controller.reverse();
      }
    }
  }
  
  /// Trigger long press feedback
  static void onKeyLongPress({
    required String keyId,
    required Offset touchPoint,
  }) {
    final controller = _animationControllers[keyId];
    if (controller != null) {
      // Continuous pulse animation
      controller.repeat(reverse: true);
      
      // Enhanced haptic feedback for long press
      if (_hapticIntensity != FeedbackIntensity.off) {
        HapticFeedback.heavyImpact();
      }
      
      // Create continuous particles
      _createParticleExplosion(touchPoint, enhanced: true);
    }
  }
  
  /// Stop long press feedback
  static void onKeyLongPressEnd(String keyId) {
    final controller = _animationControllers[keyId];
    if (controller != null) {
      controller.stop();
      controller.reset();
    }
  }
  
  /// Get current particles for rendering
  static List<Particle> get particles => List.unmodifiable(_particles);
  
  /// Get current ripples for rendering
  static List<RippleEffect> get ripples => List.unmodifiable(_ripples);
  
  // Private methods
  
  static Future<void> _triggerHapticFeedback({bool isSpecialKey = false}) async {
    if (_hapticIntensity == FeedbackIntensity.off) return;
    
    switch (_hapticIntensity) {
      case FeedbackIntensity.light:
        HapticFeedback.lightImpact();
        break;
      case FeedbackIntensity.medium:
        if (isSpecialKey) {
          HapticFeedback.mediumImpact();
        } else {
          HapticFeedback.lightImpact();
        }
        break;
      case FeedbackIntensity.strong:
        if (isSpecialKey) {
          HapticFeedback.heavyImpact();
        } else {
          HapticFeedback.mediumImpact();
        }
        break;
      case FeedbackIntensity.off:
        break;
    }
    
    // Additional haptic feedback for strong intensity
    if (_hapticIntensity == FeedbackIntensity.strong) {
      // Double haptic feedback for strong intensity
      Future.delayed(const Duration(milliseconds: 50), () {
        HapticFeedback.lightImpact();
      });
    }
  }
  
  static Future<void> _triggerSoundFeedback({
    bool isSpecialKey = false,
    bool isSpaceBar = false,
    bool isEnterKey = false,
  }) async {
    if (_soundIntensity == FeedbackIntensity.off) return;
    
    String soundFile = 'key_press.wav';
    
    if (isSpaceBar) {
      soundFile = 'space_press.wav';
    } else if (isEnterKey) {
      soundFile = 'enter_press.wav';
    } else if (isSpecialKey) {
      soundFile = 'special_key_press.wav';
    }
    
    try {
      if (_soundEnabled) {
        await _playSound(soundFile);
      }
    } catch (e) {
      // Fallback to system sound - use haptic feedback instead and disable future sound attempts
      print('Sound playback failed for $soundFile: $e');
      _soundEnabled = false;
      HapticFeedback.lightImpact();
    }
  }
  
  static void _triggerVisualFeedback({
    required String keyId,
    required Offset touchPoint,
    bool isSpecialKey = false,
    bool isSpaceBar = false,
    bool isEnterKey = false,
  }) {
    if (_visualIntensity == FeedbackIntensity.off) return;
    
    final controller = _animationControllers[keyId];
    if (controller != null) {
      // Scale and brightness animation
      controller.duration = KeyAnimationSpecs.pressDuration;
      controller.forward();
    }
    
    // Create ripple effect
    _createRippleEffect(touchPoint);
    
    // Create particle effects for special keys
    if (isSpecialKey || isSpaceBar || isEnterKey) {
      _createParticleExplosion(touchPoint, enhanced: isSpecialKey);
    }
  }
  
  static void _createRippleEffect(Offset center) {
    final ripple = RippleEffect(
      center: center,
      maxRadius: 80.0 * _getIntensityMultiplier(_visualIntensity),
      color: Colors.blue.withOpacity(0.3),
    );
    _ripples.add(ripple);
  }
  
  static void _createParticleExplosion(Offset center, {bool enhanced = false}) {
    final config = ParticleConfig(
      particleCount: enhanced ? 25 : 15,
      colors: enhanced 
        ? [Colors.orange, Colors.red, Colors.yellow, Colors.white]
        : [Colors.blue, Colors.cyan, Colors.lightBlue, Colors.white],
    );
    
    for (int i = 0; i < config.particleCount; i++) {
      final angle = (i / config.particleCount) * 2 * math.pi;
      final speed = (50 + math.Random().nextDouble() * config.maxVelocity) * 
                   _getIntensityMultiplier(_visualIntensity);
      
      final particle = Particle(
        position: center,
        velocity: Offset(
          math.cos(angle) * speed,
          math.sin(angle) * speed,
        ),
        life: config.lifetime.inMilliseconds / 1000.0,
        maxLife: config.lifetime.inMilliseconds / 1000.0,
        color: config.colors[math.Random().nextInt(config.colors.length)],
        size: 2.0 + math.Random().nextDouble() * 3.0,
      );
      
      _particles.add(particle);
    }
  }
  
  static void _updateParticleSystem() {
    const deltaTime = 1.0 / 60.0; // 60 FPS
    
    // Update particles
    _particles.removeWhere((particle) {
      particle.update(deltaTime);
      return particle.isDead;
    });
    
    // Update ripples
    _ripples.removeWhere((ripple) {
      ripple.update(deltaTime);
      return !ripple.isActive;
    });
  }
  
  static double _getIntensityMultiplier(FeedbackIntensity intensity) {
    switch (intensity) {
      case FeedbackIntensity.off: return 0.0;
      case FeedbackIntensity.light: return 0.5;
      case FeedbackIntensity.medium: return 0.8;
      case FeedbackIntensity.strong: return 1.0;
    }
  }
  
  static void _preloadSounds() {
    // Preload all short clips through SoundPool-backed players (no MediaCodec churn)
    final sounds = [
      'key_press.wav',
      'space_press.wav', 
      'enter_press.wav',
      'special_key_press.wav',
    ];
    
    for (final sound in sounds) {
      _ensureSoundPlayer(sound).then((_) {
        _soundEnabled = true;
      }).catchError((e) {
        print('Could not preload sound: $sound - $e');
      });
    }
  }

  static Future<void> _playSound(String soundFile) async {
    final player = await _ensureSoundPlayer(soundFile);
    final targetVolume =
        (_soundVolume * _getIntensityMultiplier(_soundIntensity)).clamp(0.0, 1.0);

    await player.setVolume(targetVolume);
    await player.seek(Duration.zero);
    await player.resume();
  }

  static Future<AudioPlayer> _ensureSoundPlayer(String soundFile) async {
    final cached = _soundPlayers[soundFile];
    if (cached != null) return cached;

    final player = AudioPlayer(playerId: 'feedback_$soundFile');
    await player.setPlayerMode(PlayerMode.lowLatency);
    await player.setReleaseMode(ReleaseMode.stop);
    await player.setSource(AssetSource('sounds/$soundFile'));

    _soundPlayers[soundFile] = player;
    return player;
  }
}

/// Custom painter for rendering particles and ripples
class FeedbackEffectsPainter extends CustomPainter {
  final List<Particle> particles;
  final List<RippleEffect> ripples;

  FeedbackEffectsPainter({
    required this.particles,
    required this.ripples,
  });

  @override
  void paint(Canvas canvas, Size size) {
    // Paint ripples
    for (final ripple in ripples) {
      if (!ripple.isActive) continue;
      
      final paint = Paint()
        ..color = ripple.color.withOpacity(ripple.opacity)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2.0;
      
      canvas.drawCircle(ripple.center, ripple.radius, paint);
    }
    
    // Paint particles
    for (final particle in particles) {
      final paint = Paint()
        ..color = particle.color
        ..style = PaintingStyle.fill;
      
      canvas.drawCircle(particle.position, particle.size, paint);
    }
  }

  @override
  bool shouldRepaint(FeedbackEffectsPainter oldDelegate) {
    return particles.length != oldDelegate.particles.length ||
           ripples.length != oldDelegate.ripples.length;
  }
}

/// Animated key widget with advanced feedback
class AnimatedKeyWidget extends StatefulWidget {
  final String keyId;
  final Widget child;
  final VoidCallback? onPressed;
  final VoidCallback? onLongPress;
  final bool isSpecialKey;
  final bool isSpaceBar;
  final bool isEnterKey;

  const AnimatedKeyWidget({
    super.key,
    required this.keyId,
    required this.child,
    this.onPressed,
    this.onLongPress,
    this.isSpecialKey = false,
    this.isSpaceBar = false,
    this.isEnterKey = false,
  });

  @override
  State<AnimatedKeyWidget> createState() => _AnimatedKeyWidgetState();
}

class _AnimatedKeyWidgetState extends State<AnimatedKeyWidget>
    with TickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _scaleAnimation;
  late Animation<double> _brightnessAnimation;

  @override
  void initState() {
    super.initState();
    _controller = KeyboardFeedbackSystem.getAnimationController(widget.keyId, this);
    _scaleAnimation = KeyboardFeedbackSystem.getScaleAnimation(widget.keyId)!;
    _brightnessAnimation = KeyboardFeedbackSystem.getBrightnessAnimation(widget.keyId)!;
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: (details) => _handleTapDown(details.localPosition),
      onTapUp: (_) => _handleTapUp(),
      onTap: widget.onPressed,
      onLongPress: () => _handleLongPress(Offset.zero),
      onLongPressEnd: (_) => _handleLongPressEnd(),
      child: AnimatedBuilder(
        animation: _controller,
        builder: (context, child) {
          return Transform.scale(
            scale: _scaleAnimation.value,
            child: ColorFiltered(
              colorFilter: ColorFilter.matrix(_createBrightnessMatrix(_brightnessAnimation.value)),
              child: widget.child,
            ),
          );
        },
      ),
    );
  }

  void _handleTapDown(Offset localPosition) {
    KeyboardFeedbackSystem.onKeyPress(
      keyId: widget.keyId,
      touchPoint: localPosition,
      isSpecialKey: widget.isSpecialKey,
      isSpaceBar: widget.isSpaceBar,
      isEnterKey: widget.isEnterKey,
    );
  }

  void _handleTapUp() {
    KeyboardFeedbackSystem.onKeyRelease(
      keyId: widget.keyId,
      isSpaceBar: widget.isSpaceBar,
      isEnterKey: widget.isEnterKey,
    );
  }

  void _handleLongPress(Offset localPosition) {
    KeyboardFeedbackSystem.onKeyLongPress(
      keyId: widget.keyId,
      touchPoint: localPosition,
    );
    widget.onLongPress?.call();
  }

  void _handleLongPressEnd() {
    KeyboardFeedbackSystem.onKeyLongPressEnd(widget.keyId);
  }

  List<double> _createBrightnessMatrix(double brightness) {
    return [
      brightness, 0, 0, 0, 0,
      0, brightness, 0, 0, 0,
      0, 0, brightness, 0, 0,
      0, 0, 0, 1, 0,
    ];
  }

  @override
  void dispose() {
    // Don't dispose the controller here as it's managed by KeyboardFeedbackSystem
    super.dispose();
  }
}
