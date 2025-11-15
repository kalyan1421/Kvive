import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:ai_keyboard/widgets/custom_toggle_switch.dart';
import 'package:ai_keyboard/services/clipboard_service.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'dart:convert';
import 'dart:async';

class ClipboardScreen extends StatefulWidget {
  const ClipboardScreen({super.key});

  @override
  State<ClipboardScreen> createState() => _ClipboardScreenState();
}

class _ClipboardScreenState extends State<ClipboardScreen> {
  // History Settings
  bool clipboardHistory = true;
  double cleanOldHistoryMinutes = 0.0;
  double historySize = 20.0;
  bool clearPrimaryClipAffects = true;

  // Internal Settings
  bool internalClipboard = true;
  bool syncFromSystem = true;
  bool syncToFivive = true;
  
  // Clipboard items loaded from Keyboard via MethodChannel
  List<ClipboardItem> clipboardItems = [];
  
  // Stream subscriptions
  StreamSubscription? _historySubscription;
  StreamSubscription? _newItemSubscription;
  
  @override
  void initState() {
    super.initState();
    _loadSettings();
    _loadClipboardItems();
    _setupClipboardListeners();
  }
  
  @override
  void dispose() {
    _historySubscription?.cancel();
    _newItemSubscription?.cancel();
    super.dispose();
  }
  
  /// Set up real-time listeners for clipboard changes from keyboard
  void _setupClipboardListeners() {
    // Listen for history changes
    _historySubscription = ClipboardService.onHistoryChanged.listen((items) {
      if (mounted) {
        _loadClipboardItems();
      }
    });
    
    // Listen for new items
    _newItemSubscription = ClipboardService.onNewItem.listen((item) {
      if (mounted) {
        debugPrint('📋 New clipboard item detected: ${item.text}');
        _loadClipboardItems();
      }
    });
  }
  
  Future<void> _loadSettings() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      clipboardHistory = prefs.getBool('clipboard_history') ?? true;
      cleanOldHistoryMinutes = prefs.getDouble('clean_old_history_minutes') ?? 0.0;
      historySize = prefs.getDouble('history_size') ?? 20.0;
      clearPrimaryClipAffects = prefs.getBool('clear_primary_clip_affects') ?? true;
      internalClipboard = prefs.getBool('internal_clipboard') ?? true;
      syncFromSystem = prefs.getBool('sync_from_system') ?? true;
      
    });
  }
  
  Future<void> _saveSettings() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('clipboard_history', clipboardHistory);
    await prefs.setDouble('clean_old_history_minutes', cleanOldHistoryMinutes);
    await prefs.setDouble('history_size', historySize);
    await prefs.setBool('clear_primary_clip_affects', clearPrimaryClipAffects);
    await prefs.setBool('internal_clipboard', internalClipboard);
    await prefs.setBool('sync_from_system', syncFromSystem);
   
    
    // Send settings to keyboard via MethodChannel
    debugPrint('🔧 Sending clipboard settings: enabled=$clipboardHistory, maxSize=${historySize.toInt()}');
    await ClipboardService.updateSettings({
      'enabled': clipboardHistory,  // ✅ Changed from 'clipboard_history' to 'enabled'
      'maxHistorySize': historySize.toInt(),  // ✅ Changed from 'history_size'
      'autoExpiryEnabled': cleanOldHistoryMinutes > 0,  // ✅ Changed from 'clean_old_history_minutes'
      'expiryDurationMinutes': cleanOldHistoryMinutes.toInt(),  // ✅ Convert to minutes
      'templates': [],  // ✅ Add templates (empty for now)
    });
    debugPrint('✅ Clipboard settings sent successfully');
    
    // Also send broadcast for backward compatibility
    _sendBroadcast();
  }
  
  void _sendBroadcast() {
    try {
      const platform = MethodChannel('ai_keyboard/config');
      platform.invokeMethod('sendBroadcast', {
        'action': 'com.kvive.keyboard.CLIPBOARD_CHANGED'
      });
      debugPrint('Clipboard settings broadcast sent');
    } catch (e) {
      debugPrint('Error sending broadcast: $e');
    }
  }
  
  Future<void> _loadClipboardItems() async {
    try {
      // Try to load from keyboard via MethodChannel first
      final items = await ClipboardService.getHistory(maxItems: 50);
      if (items.isNotEmpty && mounted) {
        setState(() {
          clipboardItems = items.map((item) => ClipboardItem(
            id: item.id,
            text: item.text,
            timestamp: item.timestamp,
            isPinned: item.isPinned,
          )).toList();
        });
        return;
      }
      
      // Fallback to SharedPreferences for backward compatibility
      final prefs = await SharedPreferences.getInstance();
      final itemsJson = prefs.getString('clipboard_items');
      if (itemsJson != null && mounted) {
        final List<dynamic> itemsList = json.decode(itemsJson);
        setState(() {
          clipboardItems = itemsList
              .map((item) => ClipboardItem.fromJson(item))
              .toList();
        });
      }
    } catch (e) {
      debugPrint('Error loading clipboard items: $e');
    }
  }
  
  Future<void> _togglePin(ClipboardItem item) async {
    try {
      // Use ClipboardService to toggle pin in keyboard
      final success = await ClipboardService.togglePin(item.id);
      if (success) {
        // Update local state
        final index = clipboardItems.indexWhere((i) => i.id == item.id);
        if (index != -1 && mounted) {
          setState(() {
            clipboardItems[index] = item.copyWith(isPinned: !item.isPinned);
          });
        }
        // Also save to SharedPreferences for backward compatibility
        await _saveClipboardItems();
      }
    } catch (e) {
      debugPrint('Error toggling pin: $e');
    }
  }
  
  Future<void> _deleteItem(ClipboardItem item) async {
    try {
      // Use ClipboardService to delete in keyboard
      final success = await ClipboardService.deleteItem(item.id);
      if (success && mounted) {
        setState(() {
          clipboardItems.removeWhere((i) => i.id == item.id);
        });
        // Also save to SharedPreferences for backward compatibility
        await _saveClipboardItems();
      }
    } catch (e) {
      debugPrint('Error deleting item: $e');
    }
  }
  
  Future<void> _saveClipboardItems() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final itemsJson = json.encode(
        clipboardItems.map((item) => item.toJson()).toList()
      );
      await prefs.setString('clipboard_items', itemsJson);
      _sendBroadcast();
    } catch (e) {
      debugPrint('Error saving clipboard items: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.white),
          onPressed: () => Navigator.pop(context),
        ),
        title: Text(
          'Clipboard',
          style: AppTextStyle.headlineMedium.copyWith(color: AppColors.white),
        ),
        centerTitle: false,
        backgroundColor: AppColors.primary,
        elevation: 0,
        actionsPadding: const EdgeInsets.only(right: 16),
        actions: [
          Stack(
            children: [
              Icon(Icons.notifications, color: AppColors.white, size: 24),
              Positioned(
                right: 0,
                top: 0,
                child: Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: AppColors.secondary,
                    shape: BoxShape.circle,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
      backgroundColor: AppColors.white,
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SizedBox(height: 8),

            // History Settings Section
            _buildSectionTitle('History Settings'),
            const SizedBox(height: 16),

            // Clipboard History
            _buildToggleSetting(
              title: 'Clipboard History',
              description: 'Enabled',
              value: clipboardHistory,
              onChanged: (value) {
                setState(() => clipboardHistory = value);
                _saveSettings();
              },
            ),

            const SizedBox(height: 12),

            // Clean Old History Items
            _buildSliderSetting(
              title: 'Clean Old History Items',
              portraitValue: cleanOldHistoryMinutes,
              onPortraitChanged: (value) {
                setState(() => cleanOldHistoryMinutes = value);
                _saveSettings();
              },
              min: 0.0,
              max: 60.0,
              unit: ' min',
              portraitLabel: 'Minutes',
              showLandscape: false,
            ),

            const SizedBox(height: 12),

            // History Size
            _buildSliderSetting(
              title: 'History Size',
              portraitValue: historySize,
              onPortraitChanged: (value) {
                setState(() => historySize = value);
                _saveSettings();
              },
              min: 5.0,
              max: 100.0,
              unit: ' ',
              portraitLabel: 'Items',
              showLandscape: false,
            ),

            const SizedBox(height: 12),

            // Clear primary clip affects
            _buildToggleSetting(
              title: 'Clear primary clip affects ...',
              description: 'Enabled',
              value: clearPrimaryClipAffects,
              onChanged: (value) {
                setState(() => clearPrimaryClipAffects = value);
                _saveSettings();
              },
            ),

            const SizedBox(height: 32),

            // Internal Settings Section
            _buildSectionTitle('Internal Settings'),
            const SizedBox(height: 16),

            // Internal Clipboard
            _buildToggleSetting(
              title: 'Internal Clipboard',
              description: 'Enabled',
              value: internalClipboard,
              onChanged: (value) {
                setState(() => internalClipboard = value);
                _saveSettings();
              },
            ),

            const SizedBox(height: 12),

            // Sync from system
            _buildToggleSetting(
              title: 'Sync from system',
              description: 'Sync from system clipboard',
              value: syncFromSystem,
              onChanged: (value) {
                setState(() => syncFromSystem = value);
                _saveSettings();
              },
            ),

            const SizedBox(height: 12),

            // Sync Actions
            _buildSyncActions(),

            const SizedBox(height: 32),

            // Clipboard History Section
            _buildSectionTitle('Clipboard History'),
            const SizedBox(height: 16),
            
            if (clipboardItems.isEmpty)
              Container(
                padding: const EdgeInsets.all(24),
                decoration: BoxDecoration(
                  color: AppColors.lightGrey,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Center(
                  child: Text(
                    'No clipboard items yet',
                    style: AppTextStyle.bodyMedium.copyWith(color: AppColors.grey),
                  ),
                ),
              )
            else
              ...clipboardItems.map((item) => _buildClipboardItemCard(item)).toList(),

            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }
  
  Widget _buildClipboardItemCard(ClipboardItem item) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: item.isPinned ? AppColors.secondary.withOpacity(0.1) : AppColors.lightGrey,
        borderRadius: BorderRadius.circular(12),
        border: item.isPinned 
            ? Border.all(color: AppColors.secondary, width: 2)
            : null,
      ),
      child: Row(
        children: [
          // Pin icon
          if (item.isPinned)
            Icon(Icons.push_pin, color: AppColors.secondary, size: 20),
          if (item.isPinned) const SizedBox(width: 8),
          
          // Content
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  item.text.length > 60 ? '${item.text.substring(0, 60)}...' : item.text,
                  style: AppTextStyle.titleMedium.copyWith(
                    color: AppColors.primary,
                    fontWeight: FontWeight.w600,
                  ),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
                const SizedBox(height: 4),
                Text(
                  item.getFormattedTime(),
                  style: AppTextStyle.bodySmall.copyWith(color: AppColors.grey),
                ),
              ],
            ),
          ),
          
          // Actions
          PopupMenuButton<String>(
            icon: Icon(Icons.more_vert, color: AppColors.grey, size: 20),
            onSelected: (value) => _handleClipboardItemAction(value, item),
            itemBuilder: (BuildContext context) => [
              PopupMenuItem<String>(
                value: 'pin',
                child: Row(
                  children: [
                    Icon(
                      item.isPinned ? Icons.push_pin_outlined : Icons.push_pin,
                      color: AppColors.primary,
                      size: 18,
                    ),
                    const SizedBox(width: 8),
                    Text(
                      item.isPinned ? 'Unpin' : 'Pin',
                      style: AppTextStyle.bodyMedium.copyWith(
                        color: AppColors.primary,
                      ),
                    ),
                  ],
                ),
              ),
              PopupMenuItem<String>(
                value: 'delete',
                child: Row(
                  children: [
                    Icon(Icons.delete, color: AppColors.secondary, size: 18),
                    const SizedBox(width: 8),
                    Text(
                      'Delete',
                      style: AppTextStyle.bodyMedium.copyWith(
                        color: AppColors.secondary,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
  
  void _handleClipboardItemAction(String action, ClipboardItem item) {
    switch (action) {
      case 'pin':
        _togglePin(item);
        break;
      case 'delete':
        _showDeleteConfirmation(item);
        break;
    }
  }
  
  void _showDeleteConfirmation(ClipboardItem item) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Delete Item'),
        content: Text('Are you sure you want to delete this clipboard item?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              _deleteItem(item);
              Navigator.of(context).pop();
            },
            child: Text('Delete', style: TextStyle(color: AppColors.secondary)),
          ),
        ],
      ),
    );
  }

  Widget _buildSyncActions() {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.lightGrey,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Sync Actions',
            style: AppTextStyle.titleMedium.copyWith(
              color: AppColors.primary,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 12),
         
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: _syncFromCloud,
              icon: Icon(Icons.cloud_download, size: 18),
              label: Text('Sync from Cloud'),
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.primary,
                foregroundColor: AppColors.white,
                padding: const EdgeInsets.symmetric(vertical: 12),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(8),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
  
  Future<void> _syncFromSystem() async {
    try {
      final success = await ClipboardService.syncFromSystem();
      if (success && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('✅ Synced from system clipboard')),
        );
        _loadClipboardItems();
      }
    } catch (e) {
      debugPrint('Error syncing from system: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('❌ Failed to sync from system')),
        );
      }
    }
  }
  
  Future<void> _syncToCloud() async {
    try {
      final success = await ClipboardService.syncToCloud();
      if (success && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('☁️ Synced to Kvīve Cloud')),
        );
      }
    } catch (e) {
      debugPrint('Error syncing to cloud: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('❌ Failed to sync to cloud')),
        );
      }
    }
  }
  
  Future<void> _syncFromCloud() async {
    try {
      final success = await ClipboardService.syncFromCloud();
      if (success && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('☁️ Synced from Kvīve Cloud')),
        );
        _loadClipboardItems();
      }
    } catch (e) {
      debugPrint('Error syncing from cloud: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('❌ Failed to sync from cloud')),
        );
      }
    }
  }

  Widget _buildSectionTitle(String title) {
    return Text(
      title,
      style: AppTextStyle.titleMedium.copyWith(
        color: AppColors.secondary,
        fontWeight: FontWeight.w600,
      ),
    );
  }

  Widget _buildToggleSetting({
    required String title,
    required String description,
    required bool value,
    required ValueChanged<bool> onChanged,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.lightGrey,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: AppTextStyle.titleLarge.copyWith(
                    color: AppColors.primary,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  description,
                  style: AppTextStyle.bodySmall.copyWith(color: AppColors.grey),
                ),
              ],
            ),
          ),
          CustomToggleSwitch(
            value: value,
            onChanged: onChanged,
            width: 48.0,
            height: 16.0,
            knobSize: 24.0,
          ),
        ],
      ),
    );
  }

  Widget _buildSliderSetting({
    required String title,
    required double portraitValue,
    double? landscapeValue,
    required ValueChanged<double> onPortraitChanged,
    ValueChanged<double>? onLandscapeChanged,
    required double min,
    required double max,
    required String unit,
    String portraitLabel = 'Portrait',
    String? landscapeLabel,
    bool showLandscape = true,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.lightGrey,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: AppTextStyle.titleLarge.copyWith(
              color: AppColors.black,
              fontWeight: FontWeight.w800,
            ),
          ),
          const SizedBox(height: 12),
          // Portrait Slider
          Row(
            children: [
              SizedBox(
                width: 80,
                child: Text(
                  portraitLabel,
                  style: AppTextStyle.bodyMedium.copyWith(
                    color: AppColors.grey,
                  ),
                ),
              ),
              Expanded(
                child: Slider(
                  thumbColor: AppColors.white,
                  value: portraitValue,
                  min: min,
                  max: max,
                  onChanged: onPortraitChanged,
                  activeColor: AppColors.secondary,
                  inactiveColor: AppColors.white,
                ),
              ),
              SizedBox(
                width: 50,
                child: Text(
                  '${portraitValue.toInt()}$unit',
                  style: AppTextStyle.bodySmall.copyWith(
                    color: AppColors.black,
                    fontWeight: FontWeight.w600,
                  ),
                  textAlign: TextAlign.right,
                ),
              ),
            ],
          ),
          // Landscape Slider (if enabled)
          if (showLandscape &&
              landscapeValue != null &&
              onLandscapeChanged != null) ...[
            const SizedBox(height: 8),
            Row(
              children: [
                SizedBox(
                  width: 80,
                  child: Text(
                    landscapeLabel ?? 'Landscape',
                    style: AppTextStyle.bodySmall.copyWith(
                      color: AppColors.grey,
                    ),
                  ),
                ),
                Expanded(
                  child: Slider(
                    thumbColor: AppColors.white,
                    value: landscapeValue,
                    min: min,
                    max: max,
                    onChanged: onLandscapeChanged,
                    activeColor: AppColors.secondary,
                    inactiveColor: AppColors.white,
                  ),
                ),
                SizedBox(
                  width: 50,
                  child: Text(
                    '${landscapeValue.toInt()}$unit',
                    style: AppTextStyle.bodySmall.copyWith(
                      color: AppColors.black,
                      fontWeight: FontWeight.w600,
                    ),
                    textAlign: TextAlign.right,
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

// ClipboardItem model class
class ClipboardItem {
  final String id;
  final String text;
  final int timestamp;
  final bool isPinned;
  
  ClipboardItem({
    required this.id,
    required this.text,
    required this.timestamp,
    this.isPinned = false,
  });
  
  factory ClipboardItem.fromJson(Map<String, dynamic> json) {
    return ClipboardItem(
      id: json['id'] as String,
      text: json['text'] as String,
      timestamp: json['timestamp'] as int,
      isPinned: json['isPinned'] as bool? ?? false,
    );
  }
  
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'text': text,
      'timestamp': timestamp,
      'isPinned': isPinned,
    };
  }
  
  ClipboardItem copyWith({
    String? id,
    String? text,
    int? timestamp,
    bool? isPinned,
  }) {
    return ClipboardItem(
      id: id ?? this.id,
      text: text ?? this.text,
      timestamp: timestamp ?? this.timestamp,
      isPinned: isPinned ?? this.isPinned,
    );
  }
  
  String getFormattedTime() {
    final now = DateTime.now().millisecondsSinceEpoch;
    final diff = now - timestamp;
    final minutes = diff ~/ 60000;
    final hours = diff ~/ 3600000;
    final days = diff ~/ 86400000;
    
    if (minutes < 1) return 'Just now';
    if (minutes < 60) return '${minutes}m ago';
    if (hours < 24) return '${hours}h ago';
    return '${days}d ago';
  }
}
