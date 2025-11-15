import 'package:ai_keyboard/screens/login/login_illustraion_screen.dart';
import 'package:flutter/material.dart';
import 'package:lottie/lottie.dart';

class OnboardingView extends StatefulWidget {
  const OnboardingView({super.key});

  @override
  State<OnboardingView> createState() => _OnboardingViewState();
}

class _OnboardingViewState extends State<OnboardingView> {
  final PageController _pageController = PageController();
  int _currentPage = 0;
  bool _userInteracted = false;
  bool _isAdvancing = false;
  int _animationLoopCount = 0;

  final List<OnboardingPageData> _pages = [
    OnboardingPageData(
      title: 'Welcome to Kvīve',
      description:
          'Transform your typing with AI-powered smart suggestions, effortless corrections, and more!',
      animationPath: 'assets/animations/onboarding1.json',
    ),
    OnboardingPageData(
      title: 'Smart AI Assistance',
      description:
          'Experience intelligent autocorrect, predictive text, and personalized suggestions that learn from you.',
      animationPath: 'assets/animations/onboarding2.json',
    ),
    OnboardingPageData(
      title: 'Ai Rewriting',
      description:
          'Rewrite any sentence in your perfect style. AI refines your words for clarity, tone, and impact.',
      animationPath: 'assets/animations/onboarding3.json',
    ),
  ];

  void _onAnimationComplete() {
    if (_userInteracted || _isAdvancing) return;
    _animationLoopCount++;
    if (_animationLoopCount >= 2) {
      _animationLoopCount = 0;
      _isAdvancing = true;
      Future.delayed(const Duration(milliseconds: 500), () {
        if (mounted && !_userInteracted) _nextPage();
      });
    }
  }

  void _markUserInteraction() {
    setState(() {
      _userInteracted = true;
      _isAdvancing = false;
    });
  }

  void _nextPage() {
    if (_currentPage < _pages.length - 1) {
      _pageController.nextPage(
        duration: const Duration(milliseconds: 600),
        curve: Curves.easeInOut,
      );
    } else {
      _navigateToHome();
    }
  }

  void _navigateToHome() {
    Navigator.of(context).pushReplacement(
      MaterialPageRoute(builder: (_) => const LoginIllustraionScreen()),
    );
  }

  void _onPageChanged(int page) {
    setState(() {
      _currentPage = page;
      _animationLoopCount = 0;
      _userInteracted = false;
      _isAdvancing = false;
    });
  }

  // Helper methods for responsive sizing
  double _getResponsiveSize(BuildContext context, double baseSize) {
    final screenWidth = MediaQuery.of(context).size.width;
    final screenHeight = MediaQuery.of(context).size.height;
    final diagonal = MediaQuery.of(context).size.shortestSide;
    
    // Use shortest side for consistent scaling across orientations
    if (diagonal < 600) {
      // Small phones
      return baseSize * 0.85;
    } else if (diagonal < 900) {
      // Large phones / small tablets
      return baseSize;
    } else {
      // Tablets
      return baseSize * 1.15;
    }
  }

  double _getResponsivePadding(BuildContext context, double basePadding) {
    final screenWidth = MediaQuery.of(context).size.width;
    return screenWidth * (basePadding / 375); // Base on 375px width
  }

  Widget _buildHeader(BuildContext context) {
    final topPadding = _getResponsivePadding(context, 40.0);
    final fontSize = _getResponsiveSize(context, 36);
    
    return Padding(
      padding: EdgeInsets.only(top: topPadding),
      child: Center(
        child: Text(
          'Kvīve',
          style: TextStyle(
            color: const Color(0xFFFF9900),
            fontSize: fontSize,
            fontWeight: FontWeight.bold,
            letterSpacing: fontSize * 0.033, // Responsive letter spacing
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final screenWidth = MediaQuery.of(context).size.width;
    final screenHeight = MediaQuery.of(context).size.height;
    final bottomPadding = _getResponsivePadding(context, 40);
    final horizontalPadding = _getResponsivePadding(context, 24);
    final headerTopPadding = _getResponsivePadding(context, 10);
    
    // Responsive button sizes
    final nextButtonWidth = screenWidth * 0.18; // ~18% of screen width
    final nextButtonHeight = screenHeight * 0.065; // ~6.5% of screen height
    final nextIconSize = nextButtonWidth * 0.55;
    
    // Responsive dot indicator sizes
    final dotWidth = _getResponsiveSize(context, 8);
    final dotHeight = _getResponsiveSize(context, 8);
    final activeDotWidth = _getResponsiveSize(context, 20);
    
    return Scaffold(
      backgroundColor: const Color(0xFF1A233B),
      body: SafeArea(
        bottom: false,
        child: GestureDetector(
          onTap: _markUserInteraction,
          onPanDown: (_) => _markUserInteraction(),
          onHorizontalDragStart: (_) => _markUserInteraction(),
          child: Stack(
            children: [
              /// PageView area
              PageView.builder(
                controller: _pageController,
                onPageChanged: _onPageChanged,
                itemCount: _pages.length,
                physics: const ClampingScrollPhysics(),
                itemBuilder: (context, i) => _OnboardingPage(
                  data: _pages[i],
                  onAnimationComplete: _onAnimationComplete,
                  isCurrentPage: i == _currentPage,
                  isAdvancing: _isAdvancing,
                  onNextPressed: () {
                    if (i == _pages.length - 1) {
                      _navigateToHome();
                    } else {
                      _markUserInteraction();
                      _nextPage();
                    }
                  },
                ),
              ),

              /// Kvive header
              Positioned(
                top: headerTopPadding,
                left: 0,
                right: 0,
                child: _buildHeader(context),
              ),

              /// Footer (Skip + dots)
              Positioned(
                bottom: bottomPadding,
                left: horizontalPadding,
                right: horizontalPadding,
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    if (_currentPage < _pages.length - 1)
                      OutlinedButton(
                        style: OutlinedButton.styleFrom(
                          side: const BorderSide(
                            color: Color(0xFFFF9900),
                            width: 0.5,
                          ),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(
                              _getResponsiveSize(context, 10),
                            ),
                          ),
                          padding: EdgeInsets.symmetric(
                            horizontal: _getResponsivePadding(context, 25),
                            vertical: _getResponsivePadding(context, 10),
                          ),
                        ),
                        onPressed: _navigateToHome,
                        child: Text(
                          'Skip',
                          style: TextStyle(
                            color: Colors.white,
                            fontSize: _getResponsiveSize(context, 16),
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      )
                    else
                      SizedBox(width: nextButtonWidth),
                    Row(
                      children: List.generate(
                        _pages.length,
                        (i) => AnimatedContainer(
                          duration: const Duration(milliseconds: 300),
                          width: _currentPage == i ? activeDotWidth : dotWidth,
                          height: dotHeight,
                          margin: EdgeInsets.symmetric(
                            horizontal: _getResponsivePadding(context, 4),
                          ),
                          decoration: BoxDecoration(
                            color: _currentPage == i
                                ? const Color(0xFFFF9900)
                                : Colors.grey.shade600,
                            borderRadius: BorderRadius.circular(
                              _getResponsiveSize(context, 4),
                            ),
                          ),
                        ),
                      ),
                    ),
                    GestureDetector(
                      onTap: _nextPage,
                      child: Container(
                        width: nextButtonWidth.clamp(50.0, 90.0),
                        height: nextButtonHeight.clamp(45.0, 70.0),
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(
                            _getResponsiveSize(context, 10),
                          ),
                          color: const Color(0xFFFF9900),
                          boxShadow: [
                            BoxShadow(
                              color: Colors.black.withOpacity(0.3),
                              blurRadius: _getResponsiveSize(context, 10),
                              offset: Offset(
                                0,
                                _getResponsiveSize(context, 4),
                              ),
                            ),
                          ],
                        ),
                        child: Icon(
                          Icons.keyboard_arrow_right,
                          color: Colors.white,
                          size: nextIconSize.clamp(30.0, 50.0),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class OnboardingPageData {
  final String title, description, animationPath;
  const OnboardingPageData({
    required this.title,
    required this.description,
    required this.animationPath,
  });
}

class _OnboardingPage extends StatefulWidget {
  final OnboardingPageData data;
  final VoidCallback onAnimationComplete;
  final bool isCurrentPage, isAdvancing;
  final VoidCallback onNextPressed;

  const _OnboardingPage({
    required this.data,
    required this.onAnimationComplete,
    required this.isCurrentPage,
    required this.isAdvancing,
    required this.onNextPressed,
  });

  @override
  State<_OnboardingPage> createState() => _OnboardingPageState();
}

class _OnboardingPageState extends State<_OnboardingPage>
    with TickerProviderStateMixin {
  AnimationController? _controller;

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  // Helper methods for responsive sizing
  double _getResponsiveSize(BuildContext context, double baseSize) {
    final screenWidth = MediaQuery.of(context).size.width;
    final screenHeight = MediaQuery.of(context).size.height;
    final diagonal = MediaQuery.of(context).size.shortestSide;
    
    // Use shortest side for consistent scaling across orientations
    if (diagonal < 600) {
      // Small phones
      return baseSize * 0.85;
    } else if (diagonal < 900) {
      // Large phones / small tablets
      return baseSize;
    } else {
      // Tablets
      return baseSize * 1.15;
    }
  }

  double _getResponsivePadding(BuildContext context, double basePadding) {
    final screenWidth = MediaQuery.of(context).size.width;
    return screenWidth * (basePadding / 375); // Base on 375px width
  }

  @override
  Widget build(BuildContext context) {
    final screenHeight = MediaQuery.of(context).size.height;
    final screenWidth = MediaQuery.of(context).size.width;
    
    // Responsive animation height - adjust based on screen size
    final animationHeight = screenHeight * 
        (screenHeight < 700 ? 0.55 : screenHeight < 900 ? 0.58 : 0.60);
    
    // Responsive spacing
    final titleSpacing = _getResponsivePadding(context, 20);
    final descriptionSpacing = _getResponsivePadding(context, 12);
    final horizontalPadding = _getResponsivePadding(context, 24);
    
    // Responsive font sizes
    final titleFontSize = _getResponsiveSize(context, 28);
    final descriptionFontSize = _getResponsiveSize(context, 16);

    return Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        SizedBox(
          height: animationHeight,
          child: Stack(
            alignment: Alignment.center,
            children: [
              Lottie.asset(
                widget.data.animationPath,
                fit: BoxFit.contain,
                repeat: false,
                controller: _controller,
                onLoaded: (comp) {
                  _controller?.dispose();
                  _controller = AnimationController(
                    vsync: this,
                    duration: comp.duration,
                  )
                    ..addStatusListener((status) {
                      if (status == AnimationStatus.completed &&
                          widget.isCurrentPage) {
                        widget.onAnimationComplete();
                        Future.delayed(const Duration(milliseconds: 300), () {
                          if (mounted &&
                              widget.isCurrentPage &&
                              !widget.isAdvancing) {
                            _controller?.reset();
                            _controller?.forward();
                          }
                        });
                      }
                    });
                  if (widget.isCurrentPage) _controller!.forward();
                },
              ),
            ],
          ),
        ),
        // SizedBox(height: titleSpacing),
        Padding(
          padding: EdgeInsets.symmetric(horizontal: horizontalPadding),
          child: Text(
            widget.data.title,
            textAlign: TextAlign.left,
            style: TextStyle(
              color: Colors.white,
              fontSize: titleFontSize,
              fontWeight: FontWeight.bold,
            ),
          ),
        ),
        SizedBox(height: descriptionSpacing),
        Padding(
          padding: EdgeInsets.symmetric(horizontal: horizontalPadding),
          child: Text(
            widget.data.description,
            textAlign: TextAlign.left,
            style: TextStyle(
              color: Colors.white70,
              fontSize: descriptionFontSize,
              height: 1.4,
            ),
          ),
        ),
      ],
    );
  }

  @override
  void didUpdateWidget(_OnboardingPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.isAdvancing && _controller != null) {
      _controller!.stop();
    } else if (widget.isCurrentPage && !oldWidget.isCurrentPage) {
      _controller?.reset();
      _controller?.forward();
    }
  }
}
