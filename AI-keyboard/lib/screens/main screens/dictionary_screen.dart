import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:ai_keyboard/widgets/custom_toggle_switch.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:uuid/uuid.dart';
import 'dart:convert';

class DictionaryScreen extends StatefulWidget {
  const DictionaryScreen({super.key});

  @override
  State<DictionaryScreen> createState() => _DictionaryScreenState();
}

class _DictionaryScreenState extends State<DictionaryScreen> {
  // Dictionary Settings
  bool dictionaryEnabled = true;

  // Custom Word Entries
  List<DictionaryEntry> customWords = [];
  
  @override
  void initState() {
    super.initState();
    _loadSettings();
    _loadDictionaryEntries();
  }
  
  Future<void> _loadSettings() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      dictionaryEnabled = prefs.getBool('dictionary_enabled') ?? true;
    });
  }
  
  Future<void> _saveSettings() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('dictionary_enabled', dictionaryEnabled);
    _sendBroadcast();
  }
  
  void _sendBroadcast() {
    try {
      const platform = MethodChannel('ai_keyboard/config');
      platform.invokeMethod('sendBroadcast', {
        'action': 'com.kvive.keyboard.DICTIONARY_CHANGED'
      });
      debugPrint('Dictionary settings broadcast sent');
    } catch (e) {
      debugPrint('Error sending broadcast: $e');
    }
  }
  
  Future<void> _loadDictionaryEntries() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final entriesJson = prefs.getString('dictionary_entries');
      if (entriesJson != null) {
        final List<dynamic> entriesList = json.decode(entriesJson);
        final loadedEntries = entriesList
            .map((entry) => DictionaryEntry.fromJson(entry))
            .toList();
        
        // ✅ FIX: Remove duplicates - keep only the first occurrence of each shortcut
        final Map<String, DictionaryEntry> uniqueEntries = {};
        for (var entry in loadedEntries) {
          final key = entry.shortcut.toLowerCase();
          if (!uniqueEntries.containsKey(key)) {
            uniqueEntries[key] = entry;
          }
        }
        
        setState(() {
          customWords = uniqueEntries.values.toList();
        });
        
        // Save cleaned list if duplicates were removed
        if (uniqueEntries.length != loadedEntries.length) {
          await _saveDictionaryEntries();
          debugPrint('Removed ${loadedEntries.length - uniqueEntries.length} duplicate entries');
        }
      } else {
        // Add default entries
        setState(() {
          customWords = [
            DictionaryEntry(
              id: Uuid().v4(),
              shortcut: 'gm',
              expansion: 'Good Morning',
            ),
            DictionaryEntry(
              id: Uuid().v4(),
              shortcut: 'howru',
              expansion: 'How are you?',
            ),
            DictionaryEntry(
              id: Uuid().v4(),
              shortcut: 'ga',
              expansion: 'Good Afternoon',
            ),
          ];
        });
        await _saveDictionaryEntries();
      }
    } catch (e) {
      debugPrint('Error loading dictionary entries: $e');
    }
  }
  
  Future<void> _saveDictionaryEntries() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final entriesJson = json.encode(
        customWords.map((entry) => entry.toJson()).toList()
      );
      await prefs.setString('dictionary_entries', entriesJson);
      _sendBroadcast();
    } catch (e) {
      debugPrint('Error saving dictionary entries: $e');
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
          'Dictionary',
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

            // Dictionary Section Title
            _buildSectionTitle('Dictionary'),
            const SizedBox(height: 16),

            // Dictionary Toggle
            _buildDictionaryToggle(),

            const SizedBox(height: 16),

            // Add Words Button
            _buildAddWordsButton(),

            const SizedBox(height: 16),

            // Custom Word Entries List
            if (customWords.isEmpty)
              Container(
                padding: const EdgeInsets.all(24),
                decoration: BoxDecoration(
                  color: AppColors.lightGrey,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Center(
                  child: Text(
                    'No dictionary entries yet. Add shortcuts to expand text faster!',
                    style: AppTextStyle.bodyMedium.copyWith(color: AppColors.grey),
                    textAlign: TextAlign.center,
                  ),
                ),
              )
            else
              ...customWords.map((word) => _buildWordEntryCard(word)).toList(),

            const SizedBox(height: 24),
          ],
        ),
      ),
    );
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

  Widget _buildDictionaryToggle() {
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
                  'Dictionary',
                  style: AppTextStyle.titleLarge.copyWith(
                    color: AppColors.primary,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  dictionaryEnabled ? 'Enabled' : 'Disabled',
                  style: AppTextStyle.bodySmall.copyWith(color: AppColors.grey),
                ),
              ],
            ),
          ),
          CustomToggleSwitch(
            value: dictionaryEnabled,
            onChanged: (value) {
              setState(() => dictionaryEnabled = value);
              _saveSettings();
            },
            width: 48.0,
            height: 16.0,
            knobSize: 24.0,
          ),
        ],
      ),
    );
  }

  Widget _buildAddWordsButton() {
    return SizedBox(
      width: MediaQuery.of(context).size.width * 0.4,
      child: ElevatedButton.icon(
        onPressed: () => _showAddWordDialog(),
        icon: const Icon(Icons.add, color: AppColors.white, size: 20),
        label: Text(
          'Add Words',
          style: AppTextStyle.buttonPrimary.copyWith(
            color: AppColors.white,
            fontWeight: FontWeight.w600,
          ),
        ),
        style: ElevatedButton.styleFrom(
          backgroundColor: AppColors.secondary,
          foregroundColor: AppColors.white,
          padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 16),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
          elevation: 0,
        ),
      ),
    );
  }

  Widget _buildWordEntryCard(DictionaryEntry entry) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
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
                // Expansion text
                Text(
                  entry.expansion,
                  style: AppTextStyle.titleLarge.copyWith(
                    color: AppColors.primary,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                const SizedBox(height: 4),
                // Shortcut
                Text(
                  'Shortcut: ${entry.shortcut}',
                  style: AppTextStyle.bodyMedium.copyWith(color: AppColors.grey),
                ),
                const SizedBox(height: 2),
                // Usage frequency
                Text(
                  entry.getFormattedUsageCount(),
                  style: AppTextStyle.bodySmall.copyWith(
                    color: AppColors.secondary,
                    fontSize: 11,
                  ),
                ),
              ],
            ),
          ),
          PopupMenuButton<String>(
            icon: Icon(Icons.more_vert, color: AppColors.grey, size: 20),
            onSelected: (value) => _handleWordAction(value, entry),
            itemBuilder: (BuildContext context) => [
              PopupMenuItem<String>(
                value: 'edit',
                child: Row(
                  children: [
                    Icon(Icons.edit, color: AppColors.primary, size: 18),
                    const SizedBox(width: 8),
                    Text(
                      'Edit',
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

  void _handleWordAction(String action, DictionaryEntry entry) {
    switch (action) {
      case 'edit':
        _showEditWordDialog(entry);
        break;
      case 'delete':
        _showDeleteConfirmation(entry);
        break;
    }
  }

  void _showAddWordDialog() {
    final TextEditingController expansionController = TextEditingController();
    final TextEditingController shortcutController = TextEditingController();

    showDialog(
      context: context,
      barrierDismissible: true,
      builder: (BuildContext context) {
        return Dialog(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          child: Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: AppColors.white,
              borderRadius: BorderRadius.circular(16),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Header
                Text(
                  'Add Word',
                  style: AppTextStyle.titleLarge.copyWith(
                    color: AppColors.primary,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                Divider(color: AppColors.lightGrey, thickness: 1),

                const SizedBox(height: 16),

                // Shortcut Input
                TextField(
                  controller: shortcutController,
                  decoration: InputDecoration(
                    fillColor: AppColors.lightGrey,
                    filled: true,
                    labelText: 'Shortcut (e.g., "brb")',
                    labelStyle: AppTextStyle.bodyMedium.copyWith(
                      color: AppColors.grey,
                    ),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                      borderSide: BorderSide(color: Colors.transparent),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                      borderSide: BorderSide(color: AppColors.secondary),
                    ),
                  ),
                ),

                const SizedBox(height: 16),

                // Expansion Input
                TextField(
                  controller: expansionController,
                  decoration: InputDecoration(
                    fillColor: AppColors.lightGrey,
                    filled: true,
                    labelText: 'Expansion (e.g., "be right back")',
                    labelStyle: AppTextStyle.bodyMedium.copyWith(
                      color: AppColors.grey,
                    ),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                      borderSide: BorderSide(color: Colors.transparent),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                      borderSide: BorderSide(color: AppColors.secondary),
                    ),
                  ),
                ),

                const SizedBox(height: 24),

                // Action Buttons
                Row(
                  children: [
                    Expanded(
                      child: ElevatedButton(
                        onPressed: () => Navigator.of(context).pop(),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.secondary,
                          foregroundColor: AppColors.white,
                          padding: const EdgeInsets.symmetric(vertical: 16),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(32),
                          ),
                          elevation: 0,
                        ),
                        child: Text(
                          'Cancel',
                          style: AppTextStyle.buttonSecondary.copyWith(
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: ElevatedButton(
                        onPressed: () async {
                          if (expansionController.text.isNotEmpty &&
                              shortcutController.text.isNotEmpty) {
                            setState(() {
                              final newShortcut = shortcutController.text.toLowerCase();
                              
                              // ✅ FIX: Remove any existing entry with same shortcut
                              customWords.removeWhere((e) => 
                                e.shortcut.toLowerCase() == newShortcut
                              );
                              
                              // Add new entry
                              customWords.add(DictionaryEntry(
                                id: Uuid().v4(),
                                shortcut: newShortcut,
                                expansion: expansionController.text,
                              ));
                            });
                            await _saveDictionaryEntries();
                            Navigator.of(context).pop();
                          }
                        },
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.secondary,
                          foregroundColor: AppColors.white,
                          padding: const EdgeInsets.symmetric(vertical: 16),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(32),
                          ),
                          elevation: 0,
                        ),
                        child: Text(
                          'Add',
                          style: AppTextStyle.buttonSecondary.copyWith(
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  void _showEditWordDialog(DictionaryEntry entry) {
    final TextEditingController expansionController = TextEditingController(
      text: entry.expansion,
    );
    final TextEditingController shortcutController = TextEditingController(
      text: entry.shortcut,
    );

    showDialog(
      context: context,
      barrierDismissible: true,
      builder: (BuildContext context) {
        return Dialog(
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
          child: Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: AppColors.white,
              borderRadius: BorderRadius.circular(16),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Header
                Text(
                  'Edit Word',
                  style: AppTextStyle.titleLarge.copyWith(
                    color: AppColors.primary,
                    fontWeight: FontWeight.w800,
                  ),
                ),
                Divider(color: AppColors.lightGrey, thickness: 1),

                const SizedBox(height: 16),
               
               
                // Expansion Input
                TextField(
                  controller: expansionController,
                  decoration: InputDecoration(
                    fillColor: AppColors.lightGrey,
                    filled: true,
                    labelText: 'Expansion',
                    labelStyle: AppTextStyle.bodyMedium.copyWith(
                      color: AppColors.grey,
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                      borderSide: BorderSide(color: Colors.transparent),
                    ),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                      borderSide: BorderSide(color: Colors.transparent),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                      borderSide: BorderSide(color: AppColors.secondary),
                    ),
                  ),
                ),

                const SizedBox(height: 16),
                Text(
                  'shortcut',
                  style: AppTextStyle.bodyMedium.copyWith(
                    color: AppColors.grey,
                  ),
                ),

                // Shortcut Input
                TextField(
                  controller: shortcutController,
                  decoration: InputDecoration(
                    fillColor: AppColors.lightGrey,
                    filled: true,
                    labelText: 'Shortcut',
                    labelStyle: AppTextStyle.bodyMedium.copyWith(
                      color: AppColors.grey,
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                      borderSide: BorderSide(color: Colors.transparent),
                    ),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                      borderSide: BorderSide(color: Colors.transparent),
                    ),

                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(8),
                      borderSide: BorderSide(color: AppColors.secondary),
                    ),
                  ),
                ),

                const SizedBox(height: 24),

                // Action Buttons
                Row(
                  children: [
                    Expanded(
                      child: ElevatedButton(
                        onPressed: () => Navigator.of(context).pop(),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.secondary,
                          foregroundColor: AppColors.white,
                          padding: const EdgeInsets.symmetric(vertical: 16),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(32),
                          ),
                          elevation: 0,
                        ),
                        child: Text(
                          'Cancel',
                          style: AppTextStyle.buttonSecondary.copyWith(
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: ElevatedButton(
                        onPressed: () async {
                          if (expansionController.text.isNotEmpty &&
                              shortcutController.text.isNotEmpty) {
                            setState(() {
                              final oldShortcut = entry.shortcut.toLowerCase();
                              final newShortcut = shortcutController.text.toLowerCase();
                              final newExpansion = expansionController.text;
                              
                              // ✅ FIX: Remove any duplicate shortcuts with the new shortcut (except current entry)
                              customWords.removeWhere((e) => 
                                e.id != entry.id && 
                                e.shortcut.toLowerCase() == newShortcut
                              );
                              
                              // ✅ FIX: If shortcut changed, remove any entry with the old shortcut (including current entry)
                              // This ensures the old shortcut is completely removed
                              if (oldShortcut != newShortcut) {
                                customWords.removeWhere((e) => 
                                  e.shortcut.toLowerCase() == oldShortcut
                                );
                              }
                              
                              // Update or add the entry with new shortcut
                              int index = customWords.indexWhere((e) => e.id == entry.id);
                              if (index != -1) {
                                customWords[index] = entry.copyWith(
                                  shortcut: newShortcut,
                                  expansion: newExpansion,
                                );
                              } else {
                                // If entry was removed (because shortcut changed), add it back with new shortcut
                                customWords.add(entry.copyWith(
                                  shortcut: newShortcut,
                                  expansion: newExpansion,
                                ));
                              }
                            });
                            await _saveDictionaryEntries();
                            Navigator.of(context).pop();
                          }
                        },
                        style: ElevatedButton.styleFrom(
                          backgroundColor: AppColors.secondary,
                          foregroundColor: AppColors.white,
                          padding: const EdgeInsets.symmetric(vertical: 16),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(32),
                          ),
                          elevation: 0,
                        ),
                        child: Text(
                          'Save',
                          style: AppTextStyle.buttonSecondary.copyWith(
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  void _showDeleteConfirmation(DictionaryEntry entry) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Delete Entry'),
        content: Text('Are you sure you want to delete "${entry.shortcut}" → "${entry.expansion}"?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: Text('Cancel'),
          ),
          TextButton(
            onPressed: () async {
              setState(() {
                customWords.removeWhere((e) => e.id == entry.id);
              });
              await _saveDictionaryEntries();
              Navigator.of(context).pop();
            },
            child: Text('Delete', style: TextStyle(color: AppColors.secondary)),
          ),
        ],
      ),
    );
  }
}

// DictionaryEntry model class
class DictionaryEntry {
  final String id;
  final String shortcut;
  final String expansion;
  final int usageCount;
  final int timestamp;
  
  DictionaryEntry({
    required this.id,
    required this.shortcut,
    required this.expansion,
    this.usageCount = 0,
    int? timestamp,
  }) : timestamp = timestamp ?? DateTime.now().millisecondsSinceEpoch;
  
  factory DictionaryEntry.fromJson(Map<String, dynamic> json) {
    return DictionaryEntry(
      id: json['id'] as String,
      shortcut: json['shortcut'] as String,
      expansion: json['expansion'] as String,
      usageCount: json['usageCount'] as int? ?? 0,
      timestamp: json['timestamp'] as int? ?? DateTime.now().millisecondsSinceEpoch,
    );
  }
  
  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'shortcut': shortcut,
      'expansion': expansion,
      'usageCount': usageCount,
      'timestamp': timestamp,
    };
  }
  
  DictionaryEntry copyWith({
    String? id,
    String? shortcut,
    String? expansion,
    int? usageCount,
    int? timestamp,
  }) {
    return DictionaryEntry(
      id: id ?? this.id,
      shortcut: shortcut ?? this.shortcut,
      expansion: expansion ?? this.expansion,
      usageCount: usageCount ?? this.usageCount,
      timestamp: timestamp ?? this.timestamp,
    );
  }
  
  String getFormattedUsageCount() {
    if (usageCount == 0) return 'Never used';
    if (usageCount == 1) return 'Used once';
    if (usageCount < 10) return 'Used $usageCount times';
    return 'Used $usageCount+ times';
  }
}
