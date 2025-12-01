import 'dart:convert';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

// Language preference constants (reuse across Flutter/Kotlin)
const kEnabledLanguagesKey = 'flutter.enabled_languages';
const kDefaultLanguageKey = 'flutter.default_language';
const kCurrentLanguageKey = 'flutter.current_language';
const kMultilingualEnabledKey = 'flutter.multilingual_enabled';

/// Download progress state for individual languages
class LanguageDownloadState {
  final String language;
  final double progress;
  final String status;
  final String? error;
  final bool isDownloading;
  
  LanguageDownloadState({
    required this.language,
    this.progress = 0.0,
    this.status = 'pending',
    this.error,
    this.isDownloading = false,
  });
  
  LanguageDownloadState copyWith({
    String? language,
    double? progress,
    String? status,
    String? error,
    bool? isDownloading,
  }) {
    return LanguageDownloadState(
      language: language ?? this.language,
      progress: progress ?? this.progress,
      status: status ?? this.status,
      error: error ?? this.error,
      isDownloading: isDownloading ?? this.isDownloading,
    );
  }
}

class LanguageScreen extends StatefulWidget {
  const LanguageScreen({Key? key}) : super(key: key);

  @override
  State<LanguageScreen> createState() => _LanguageScreenState();
}

class _LanguageScreenState extends State<LanguageScreen> {
  // MethodChannel for language configuration
  static const _configChannel = MethodChannel('ai_keyboard/config');
  
  // MethodChannel for language downloads
  static const _languageChannel = MethodChannel('com.kvive.keyboard/language');
  
  // Download progress tracking
  Map<String, LanguageDownloadState> _downloadStates = {};
  
  // Track selected language codes (e.g., ['en', 'hi', 'te'])
  List<String> selectedLanguages = [];
  
  // Multilingual mode toggle
  bool _multilingualEnabled = false;
  
  // Phase 2: Transliteration toggles
  bool _transliterationEnabled = true;
  bool _reverseTransliterationEnabled = false;
  
  // Loading state
  bool _isLoading = true;
  
  // Available languages from Firebase Storage (loaded from assets)
  Map<String, String> _availableLanguages = {};
  
  // Search functionality
  final TextEditingController _searchController = TextEditingController();
  String _searchQuery = '';
  
  @override
  void initState() {
    super.initState();
    _loadAvailableLanguages();
    _setupLanguageProgressListener();
    _searchController.addListener(_onSearchChanged);
  }
  
  /// Handle search query changes
  void _onSearchChanged() {
    setState(() {
      _searchQuery = _searchController.text.toLowerCase();
    });
  }
  
  /// Load available languages from Firebase Storage (via assets)
  Future<void> _loadAvailableLanguages() async {
    try {
      // Load the available_languages.json from assets
      final String jsonString = await rootBundle.loadString(
        'assets/dictionaries/available_languages.json'
      );
      
      final Map<String, dynamic> jsonData = json.decode(jsonString);
      final List<dynamic> languages = jsonData['languages'] ?? [];
      
      // Build the language map from available languages
      final Map<String, String> availableLangs = {};
      for (var lang in languages) {
        final code = lang['code'] as String;
        final name = lang['name'] as String;
        availableLangs[code] = name;
      }
      
      setState(() {
        _availableLanguages = availableLangs;
      });
      
      // Now load user preferences
      await _loadLanguagePreferences();
      
      debugPrint('✅ Loaded ${_availableLanguages.length} available languages from Firebase Storage');
    } catch (e) {
      debugPrint('❌ Error loading available languages: $e');
      // Fallback to a basic set if loading fails
      setState(() {
        _availableLanguages = {
          'en': 'English',
          'hi': 'Hindi',
          'te': 'Telugu',
          'ta': 'Tamil',
          'ur': 'Urdu',
        };
      });
      await _loadLanguagePreferences();
    }
  }
  
  /// Set up listener for language download progress updates from Kotlin
  void _setupLanguageProgressListener() {
    _languageChannel.setMethodCallHandler((call) async {
      if (call.method == 'languageDownloadProgress') {
        final args = call.arguments as Map<dynamic, dynamic>;
        final lang = args['lang'] as String;
        final progress = (args['progress'] as num).toDouble();
        final status = args['status'] as String;
        final error = args['error'] as String?;
        
        debugPrint('📥 Language download progress: $lang - $progress% ($status)');
        
        setState(() {
          _downloadStates[lang] = LanguageDownloadState(
            language: lang,
            progress: progress,
            status: status,
            error: error,
            isDownloading: progress < 100 && status != 'completed',
          );
        });
        
        // Handle completion - trigger keyboard refresh
        if (status == 'completed' || status == 'offline_enabled') {
          debugPrint('✅ Language download completed: $lang');
          
          // ✅ CRITICAL FIX: Send broadcast to refresh keyboard with new language
          try {
            await _configChannel.invokeMethod('broadcastSettingsChanged');
            debugPrint('✅ Keyboard refresh broadcast sent after $lang download');
          } catch (e) {
            debugPrint('⚠️ broadcastSettingsChanged failed: $e');
          }
          
          Future.delayed(const Duration(seconds: 1), () {
            if (mounted) {
              setState(() {
                _downloadStates.remove(lang);
              });
            }
          });
        }
      }
    });
  }
  
  /// Load language preferences from SharedPreferences
  Future<void> _loadLanguagePreferences() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      
      // Load enabled languages (Flutter uses "flutter." prefix for FlutterSharedPreferences)
      final enabledLangs = prefs.getStringList(kEnabledLanguagesKey) ?? ['en'];
      final multiEnabled = prefs.getBool(kMultilingualEnabledKey) ?? false;
      
      // Phase 2: Load transliteration toggles
      final translitEnabled = prefs.getBool('transliteration_enabled') ?? true;
      final reverseEnabled = prefs.getBool('reverse_transliteration_enabled') ?? false;
      
      setState(() {
        selectedLanguages = enabledLangs;
        _multilingualEnabled = multiEnabled;
        _transliterationEnabled = translitEnabled;
        _reverseTransliterationEnabled = reverseEnabled;
        _isLoading = false;
      });
    } catch (e) {
      debugPrint('Error loading language preferences: $e');
      setState(() {
        selectedLanguages = ['en']; // Default to English
        _isLoading = false;
      });
    }
  }
  
  /// Save languages and notify Kotlin via MethodChannel
  Future<void> _saveLanguagesAndNotify() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      
      // ✅ CRITICAL FIX: Save as StringList (Flutter format)
      await prefs.setStringList(kEnabledLanguagesKey, selectedLanguages);
      
      // Set default language to first in list if not set
      if ((prefs.getString(kDefaultLanguageKey) ?? '').isEmpty && selectedLanguages.isNotEmpty) {
        await prefs.setString(kDefaultLanguageKey, selectedLanguages.first);
      }
      
      // Set current language if not set or if current is no longer in list
      var currentLang = prefs.getString(kCurrentLanguageKey);
      if (currentLang == null || currentLang.isEmpty || !selectedLanguages.contains(currentLang)) {
        currentLang = selectedLanguages.isNotEmpty ? selectedLanguages.first : 'en';
        await prefs.setString(kCurrentLanguageKey, currentLang);
      }
      
      debugPrint('💾 Saving languages: enabled=$selectedLanguages, current=$currentLang');
      
      // ✅ CRITICAL: Notify Kotlin via MethodChannel to update keyboard service
      await _configChannel.invokeMethod('setEnabledLanguages', {
        'enabled': selectedLanguages,
        'current': currentLang,
      });
      
      // ✅ Also send settings changed broadcast to force keyboard reload
      try {
        await _configChannel.invokeMethod('broadcastSettingsChanged');
        debugPrint('✅ Settings changed broadcast sent');
      } catch (e) {
        debugPrint('⚠️ broadcastSettingsChanged failed (non-critical): $e');
      }
      
      // Show success feedback
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Language settings saved'),
            duration: Duration(seconds: 2),
          ),
        );
      }
    } catch (e) {
      debugPrint('❌ Error saving language preferences: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Error saving: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }
  
  
  /// Add a language with Firebase download support
  Future<void> _addLanguage(String langCode) async {
    if (selectedLanguages.contains(langCode)) {
      return;
    }
    
    // Show loading state immediately
    setState(() {
      _downloadStates[langCode] = LanguageDownloadState(
        language: langCode,
        progress: 0.0,
        status: 'starting',
        isDownloading: true,
      );
    });
    
    try {
      // Call Kotlin to download language data
      await _languageChannel.invokeMethod('downloadLanguageData', {'lang': langCode});
      
      // Add to selected languages after successful download initiation
      setState(() {
        if (!selectedLanguages.contains(langCode)) {
          selectedLanguages.add(langCode);
        }
      });
      
      // Save preferences
      await _saveLanguagesAndNotify();
      
    } catch (e) {
      debugPrint('Error downloading language $langCode: $e');
      
      // Update state to show error
      setState(() {
        _downloadStates[langCode] = LanguageDownloadState(
          language: langCode,
          progress: 0.0,
          status: 'error',
          error: e.toString(),
          isDownloading: false,
        );
      });
      
      // Clear error after 3 seconds
      Future.delayed(const Duration(seconds: 3), () {
        if (mounted) {
          setState(() {
            _downloadStates.remove(langCode);
          });
        }
      });
      
      // Still add language offline
      setState(() {
        if (!selectedLanguages.contains(langCode)) {
          selectedLanguages.add(langCode);
        }
      });
      
      await _saveLanguagesAndNotify();
      
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Added $langCode offline. Will download when online.'),
            backgroundColor: Colors.orange,
            duration: const Duration(seconds: 3),
          ),
        );
      }
    }
  }
  
  /// Remove a language
  void _removeLanguage(String langCode) {
    if (selectedLanguages.length > 1) { // Keep at least one language
      setState(() {
        selectedLanguages.remove(langCode);
      });
      _saveLanguagesAndNotify();
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('At least one language must be selected'),
          duration: Duration(seconds: 2),
        ),
      );
    }
  }
  
  /// Get display name for language code
  String _getLanguageName(String code) {
    return _availableLanguages[code] ?? code.toUpperCase();
  }
  
  /// Get available (not selected) languages from Firebase Storage
  List<String> _getAvailableLanguageCodes() {
    var availableCodes = _availableLanguages.keys
        .where((code) => !selectedLanguages.contains(code))
        .toList();
    
    // Filter by search query if present
    if (_searchQuery.isNotEmpty) {
      availableCodes = availableCodes.where((code) {
        final languageName = _getLanguageName(code).toLowerCase();
        return languageName.contains(_searchQuery) || code.toLowerCase().contains(_searchQuery);
      }).toList();
    }
    
    return availableCodes;
  }

  @override
  Widget build(BuildContext context) {
    if (_isLoading) {
      return Scaffold(
        backgroundColor: AppColors.white,
        appBar: AppBar(
          toolbarHeight: 70,
          backgroundColor: AppColors.primary,
          elevation: 0,
          leading: IconButton(
            icon: const Icon(Icons.arrow_back, color: AppColors.white),
            onPressed: () => Navigator.pop(context),
          ),
          title: Text(
            'Language',
            style: AppTextStyle.headlineMedium.copyWith(color: AppColors.white),
          ),
        ),
        body: const Center(child: CircularProgressIndicator()),
      );
    }
    
    return Scaffold(
      backgroundColor: AppColors.white,
      appBar: AppBar(
        toolbarHeight: 70,
        backgroundColor: AppColors.primary,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.white),
          onPressed: () => Navigator.pop(context),
        ),
        title: Text(
          'Language',
          style: AppTextStyle.headlineMedium.copyWith(color: AppColors.white),
        ),
        actions: [
          IconButton(
            icon: const Icon(
              Icons.notifications_outlined,
              color: AppColors.white,
            ),
            onPressed: () {},
          ),
        ],
      ),
      body: SafeArea(child: SingleChildScrollView(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Language Information Card
            _buildLanguageInfoCard(),

            
            
            // Multilingual Mode Toggle
           
            
            // // Phase 2: Transliteration Toggles
            // Container(
            //   margin: const EdgeInsets.symmetric(horizontal: 24),
            //   decoration: BoxDecoration(
            //     color: Theme.of(context).cardColor,
            //     borderRadius: BorderRadius.circular(12),
            //   ),
            //   child: SwitchListTile(
            //     title: const Text('Transliteration (Roman → Native)'),
            //     subtitle: const Text('Type in English to get native script'),
            //     value: _transliterationEnabled,
            //     onChanged: _toggleTransliteration,
            //     activeColor: AppColors.secondary,
            //   ),
            // ),
            
            // const SizedBox(height: 12),
            
            // Container(
            //   margin: const EdgeInsets.symmetric(horizontal: 24),
            //   decoration: BoxDecoration(
            //     color: Theme.of(context).cardColor,
            //     borderRadius: BorderRadius.circular(12),
            //   ),
            //   child: SwitchListTile(
            //     title: const Text('Reverse Transliteration (Native → Roman)'),
            //     subtitle: const Text('Convert native script back to English'),
            //     value: _reverseTransliterationEnabled,
            //     onChanged: _toggleReverseTransliteration,
            //     activeColor: AppColors.secondary,
            //   ),
            // ),

            const SizedBox(height: 24),

            // Selected Languages Section
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Selected Languages',
                    style: AppTextStyle.headlineSmall,
                  ),
                  Text(
                    '${selectedLanguages.length} active',
                    style: AppTextStyle.bodyMedium.copyWith(color: AppColors.grey),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // Selected Languages List (Reorderable)
            if (selectedLanguages.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 24),
                child: Text(
                  'No languages selected',
                  style: AppTextStyle.bodyMedium.copyWith(color: AppColors.grey),
                ),
              )
            else
              ...selectedLanguages.asMap().entries.map(
                (entry) => _buildSelectedLanguageTile(entry.value, entry.key),
              ),

            const SizedBox(height: 24),

            // Available Languages Section with Search
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              child: Text(
                'Available Languages',
                style: AppTextStyle.headlineSmall,
              ),
            ),
            const SizedBox(height: 16),
            
            // Search Bar (only show if there are available languages)
            if (_availableLanguages.keys.where((code) => !selectedLanguages.contains(code)).isNotEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 24),
                child: _buildSearchBar(),
              ),
            
            if (_availableLanguages.keys.where((code) => !selectedLanguages.contains(code)).isNotEmpty)
              const SizedBox(height: 16),

            // Available Languages List
            ..._getAvailableLanguageCodes().map(
              (langCode) => _buildAvailableLanguageTile(langCode),
            ),
            
            // Show "No results" message if search returns empty
            if (_searchQuery.isNotEmpty && _getAvailableLanguageCodes().isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
                child: Center(
                  child: Column(
                    children: [
                      Icon(Icons.search_off, size: 48, color: AppColors.grey),
                      const SizedBox(height: 16),
                      Text(
                        'No languages found',
                        style: AppTextStyle.headlineSmall.copyWith(color: AppColors.grey),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Try searching with a different term',
                        style: AppTextStyle.bodyMedium.copyWith(color: AppColors.grey),
                      ),
                    ],
                  ),
                ),
              ),

            const SizedBox(height: 24),
          ],
        ),),
      ),
    );
  }

  Widget _buildLanguageInfoCard() {
    return Container(
      margin: const EdgeInsets.all(24),
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppColors.lightGrey,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        children: [
          // Left language icon
          Image.asset(AppIcons.languages, width: 24, height: 24),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Language', style: AppTextStyle.headlineSmall),
                const SizedBox(height: 4),
                Text(
                  'Select multiple languages',
                  style: AppTextStyle.bodyMedium.copyWith(
                    color: AppColors.grey,
                  ),
                ),
              ],
            ),
          ),
          // Right language icon
          Image.asset(AppAssets.languages_image, width: 40, height: 40),
        ],
      ),
    );
  }
  
  Widget _buildSearchBar() {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.lightGrey,
        borderRadius: BorderRadius.circular(12),
      ),
      child: TextField(
        controller: _searchController,
        style: AppTextStyle.bodyMedium,
        decoration: InputDecoration(
          hintText: 'Search languages...',
          hintStyle: AppTextStyle.bodyMedium.copyWith(color: AppColors.grey),
          prefixIcon: Icon(Icons.search, color: AppColors.grey),
          suffixIcon: _searchQuery.isNotEmpty
              ? IconButton(
                  icon: Icon(Icons.clear, color: AppColors.grey),
                  onPressed: () {
                    _searchController.clear();
                  },
                )
              : null,
          border: InputBorder.none,
          contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        ),
      ),
    );
  }

  Widget _buildSelectedLanguageTile(String langCode, int index) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 24, vertical: 4),
      child: _buildTileOption(
        title: _getLanguageName(langCode),
        subtitle: index == 0 ? 'Default • QWERTY' : 'QWERTY',
        icon: Icons.check_box,
        isSelected: true,
        onTap: () => _removeLanguage(langCode),
        trailing: index == 0
            ? Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(
                  color: AppColors.secondary.withValues(alpha: 0.2),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(
                  'DEFAULT',
                  style: AppTextStyle.bodySmall.copyWith(
                    color: AppColors.secondary,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              )
            : null,
      ),
    );
  }

  Widget _buildAvailableLanguageTile(String langCode) {
    final downloadState = _downloadStates[langCode];
    final isDownloading = downloadState?.isDownloading ?? false;
    
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 24, vertical: 4),
      child: _buildTileOption(
        title: _getLanguageName(langCode),
        subtitle: isDownloading ? _getDownloadSubtitle(downloadState!) : 'QWERTY',
        icon: isDownloading ? Icons.download : Icons.add_circle_outline,
        isDownloadable: !isDownloading,
        downloadProgress: isDownloading ? downloadState!.progress : null,
        downloadStatus: downloadState?.status,
        onTap: isDownloading ? null : () => _addLanguage(langCode),
      ),
    );
  }
  
  /// Get download subtitle based on status
  String _getDownloadSubtitle(LanguageDownloadState state) {
    switch (state.status) {
      case 'starting':
        return 'Preparing download...';
      case 'downloading_transliteration':
        return 'Downloading transliteration...';
      case 'completed':
        return 'Download complete!';
      case 'offline_enabled':
        return 'Added offline';
      case 'error':
        return 'Error: ${state.error ?? "Unknown"}';
      default:
        return 'Downloading... ${state.progress.toInt()}%';
    }
  }

  Widget _buildTileOption({
    required String title,
    required String subtitle,
    required IconData icon,
    VoidCallback? onTap,
    bool isSelected = false,
    bool isDownloadable = false,
    Widget? trailing,
    double? downloadProgress,
    String? downloadStatus,
  }) {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.lightGrey,
        borderRadius: BorderRadius.circular(12),
      ),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(horizontal: 24, vertical: 4),
        minTileHeight: 72,
        onTap: onTap,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        leading: _buildLeadingIcon(icon, isSelected, isDownloadable),
        title: Text(title, style: AppTextStyle.headlineSmall),
        subtitle: Text(
          subtitle,
          style: AppTextStyle.bodyMedium.copyWith(color: AppColors.grey),
        ),
        trailing: trailing ??
            _buildTrailingWidget(isDownloadable, downloadProgress, downloadStatus),
      ),
    );
  }

  Widget _buildLeadingIcon(IconData icon, bool isSelected, bool isDownloadable) {
    if (isSelected) {
      return Container(
        width: 24,
        height: 24,
        decoration: BoxDecoration(
          color: AppColors.secondary,
          borderRadius: BorderRadius.circular(4),
        ),
        child: const Icon(Icons.check, color: AppColors.white, size: 16),
      );
    } else if (isDownloadable) {
      return Container(
        width: 24,
        height: 24,
        decoration: BoxDecoration(
          color: AppColors.secondary,
          borderRadius: BorderRadius.circular(12),
        ),
        child: const Icon(Icons.add, color: AppColors.white, size: 16),
      );
    } else {
      return Icon(icon, color: AppColors.grey, size: 24);
    }
  }
  
  /// Build trailing widget with download progress or download button
  Widget? _buildTrailingWidget(bool isDownloadable, double? downloadProgress, String? downloadStatus) {
    if (downloadProgress != null) {
      // Show progress indicator
      return SizedBox(
        width: 40,
        height: 40,
        child: Stack(
          alignment: Alignment.center,
          children: [
            CircularProgressIndicator(
              value: downloadProgress / 100,
              strokeWidth: 2,
              valueColor: AlwaysStoppedAnimation<Color>(AppColors.secondary),
            ),
            Text(
              '${downloadProgress.toInt()}%',
              style: AppTextStyle.bodySmall.copyWith(
                fontSize: 10,
                fontWeight: FontWeight.bold,
              ),
            ),
          ],
        ),
      );
    } else if (isDownloadable) {
      return Image.asset(AppIcons.download_button, width: 24, height: 24);
    }
    return null;
  }
  
  @override
  void dispose() {
    // Clean up method channel listener
    _languageChannel.setMethodCallHandler(null);
    _searchController.dispose();
    super.dispose();
  }
}
