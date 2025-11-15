import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:ai_keyboard/services/unified_ai.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:cloud_firestore/cloud_firestore.dart';

class AIWritingAssistanceScreen extends StatefulWidget {
  final int initialTabIndex;  // ✅ Add parameter for deep linking
  
  const AIWritingAssistanceScreen({
    Key? key,
    this.initialTabIndex = 0,  // Default to Popular tab
  }) : super(key: key);

  @override
  State<AIWritingAssistanceScreen> createState() =>
      _AIWritingAssistanceScreenState();
}

class _AIWritingAssistanceScreenState extends State<AIWritingAssistanceScreen> with SingleTickerProviderStateMixin {
  static const promptChannel = MethodChannel('ai_keyboard/prompts');
  
  late TabController _tabController;
  
  // Track active features (Popular tab)
  final Set<String> _activeFeatures = {'humanise', 'reply'};
  static const List<String> _featureKeys = [
    'humanise',
    'reply',
    'continue_writing',
    'facebook_post',
    'instagram_caption',
    'phrase_to_emoji',
    'summary',
  ];
  final Set<String> _processingFeatures = <String>{};
  
  // Custom prompts (Custom Assistance tab)
  List<Map<String, dynamic>> _customPrompts = [];
  bool _isLoadingCustom = true;
  
  // AI service status
  bool _isAIReady = false;
  
  // Firebase user
  User? _currentUser;
  
  @override
  void initState() {
    super.initState();
    _tabController = TabController(
      length: 2, 
      vsync: this,
      initialIndex: widget.initialTabIndex,  // ✅ Use initial tab from parameter
    );
    _currentUser = FirebaseAuth.instance.currentUser;
    _loadAIStatus();
    _loadActiveFeatures();
    _loadCustomPrompts();
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }
  
  Future<void> _loadActiveFeatures() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      
      final loadedFeatures = <String>{};
      var hasStoredPreference = false;

      for (String feature in _featureKeys) {
        if (prefs.containsKey('flutter.ai_writing_$feature')) {
          hasStoredPreference = true;
        }
        if (prefs.getBool('flutter.ai_writing_$feature') ?? false) {
          loadedFeatures.add(feature);
        }
      }
      
      if (loadedFeatures.isEmpty && !hasStoredPreference) {
        loadedFeatures.addAll(['humanise', 'reply']);
      }
      
      setState(() {
        _activeFeatures.clear();
        _activeFeatures.addAll(loadedFeatures);
      });
    } catch (e) {
      debugPrint('Error loading AI writing features: $e');
    }
  }
  
  Future<void> _loadCustomPrompts() async {
    try {
      final result = await promptChannel.invokeMethod('getPrompts', {'category': 'assistant'});
      if (result is List) {
        setState(() {
          _customPrompts = result.map((e) => Map<String, dynamic>.from(e)).toList();
          _isLoadingCustom = false;
        });
      }
    } catch (e) {
      debugPrint('Error loading custom prompts: $e');
      setState(() => _isLoadingCustom = false);
    }
  }
  
  Future<void> _loadAIStatus() async {
    try {
      final isReady = await UnifiedAI.isReady();
      setState(() => _isAIReady = isReady);
    } catch (e) {
      debugPrint('Error loading AI status: $e');
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.white,
      appBar: AppBar(
        backgroundColor: AppColors.primary,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.white),
          onPressed: () => Navigator.pop(context),
        ),
        title: Text(
          'AI Writing Assistance',
          style: AppTextStyle.headlineMedium.copyWith(color: AppColors.white),
        ),
        centerTitle: false,
        actions: [
          Stack(
            children: [
              IconButton(
                icon: const Icon(Icons.notifications_outlined, color: AppColors.white),
                onPressed: () {},
              ),
              Positioned(
                right: 8,
                top: 8,
                child: Container(
                  width: 8,
                  height: 8,
                  decoration: const BoxDecoration(
                    color: AppColors.secondary,
                    shape: BoxShape.circle,
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
      body: SafeArea(
        child: Column(
        children: [
          // Tab Bar
          Container(
            color: AppColors.white,
            child: TabBar(
              controller: _tabController,
              labelColor: AppColors.white,
              unselectedLabelColor: AppColors.secondary,
              indicator: BoxDecoration(
                color: AppColors.secondary,
                borderRadius: BorderRadius.circular(30),
              ),
              indicatorSize: TabBarIndicatorSize.tab,
              dividerColor: Colors.transparent,
              labelStyle: AppTextStyle.titleMedium.copyWith(fontWeight: FontWeight.w700),
              unselectedLabelStyle: AppTextStyle.titleMedium.copyWith(fontWeight: FontWeight.w600),
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              tabs: const [
                Tab(text: 'Popular'),
                Tab(text: 'Custom Assistance'),
              ],
            ),
          ),
          
          // Tab Views
          Expanded(
            child: TabBarView(
              controller: _tabController,
              children: [
                _buildPopularTab(),
                _buildCustomAssistanceTab(),
              ],
            ),
          ),
        ],
      )),
    );
  }

  // Popular Tab - Built-in features
  Widget _buildPopularTab() {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            _buildFeatureCard(
              id: 'humanise',
            icon: '👔',
              title: 'Humanise',
            description: 'Speak like a human',
            ),
            const SizedBox(height: 12),
            _buildFeatureCard(
              id: 'reply',
            icon: '💬',
              title: 'Reply',
            description: 'One Single Response to a message you just receiv...',
            ),
            const SizedBox(height: 12),
            _buildFeatureCard(
              id: 'continue_writing',
            icon: '📝',
              title: 'Continue Writing',
            description: 'Provide the starting of a sentence to autocomplete',
            ),
            const SizedBox(height: 12),
            _buildFeatureCard(
              id: 'facebook_post',
            icon: '🔵',
              title: 'Facebook Post',
            description: 'Create a Caption for your Facebook post',
            ),
            const SizedBox(height: 12),
            _buildFeatureCard(
              id: 'instagram_caption',
            icon: '📷',
              title: 'Instagram Caption',
            description: 'Create an Instagram Caption',
            ),
            const SizedBox(height: 12),
            _buildFeatureCard(
              id: 'phrase_to_emoji',
            icon: '😀',
              title: 'Phrase to Emoji',
            description: 'Convert a phrase to emojis',
            ),
            const SizedBox(height: 12),
            _buildFeatureCard(
              id: 'summary',
            icon: '📚',
              title: 'Summary',
            description: 'Summarise a block of text',
          ),
        ],
      ),
    );
  }

  Widget _buildFeatureCard({
    required String id,
    required String icon,
    required String title,
    required String description,
  }) {
    final bool isActive = _activeFeatures.contains(id);
    final bool isProcessing = _processingFeatures.contains(id);

    return Container(
      padding: const EdgeInsets.all(20),
        decoration: BoxDecoration(
        color: AppColors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.lightGrey, width: 1),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: 10,
            offset: const Offset(0, 2),
          ),
        ],
        ),
        child: Row(
          children: [
            // Icon
          Container(
            width: 48,
            height: 48,
            decoration: BoxDecoration(
              color: AppColors.lightGrey.withOpacity(0.5),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Center(
              child: Text(icon, style: const TextStyle(fontSize: 24)),
            ),
          ),
            const SizedBox(width: 16),

            // Title and Description
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                  style: AppTextStyle.titleLarge.copyWith(
                    fontWeight: FontWeight.w700,
                      color: AppColors.black,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    description,
                  style: AppTextStyle.bodySmall.copyWith(color: AppColors.grey),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),

          const SizedBox(width: 12),

          // Action Button
          GestureDetector(
            onTap: isProcessing ? null : () => _onFeatureTap(id),
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              decoration: BoxDecoration(
                color: isActive ? Colors.green.withOpacity(0.1) : AppColors.secondary.withOpacity(0.1),
                borderRadius: BorderRadius.circular(20),
                border: Border.all(
                  color: isActive ? Colors.green : AppColors.secondary,
                  width: 1,
                ),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (isProcessing)
                    SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        valueColor: AlwaysStoppedAnimation<Color>(
                          isActive ? Colors.green : AppColors.secondary,
                        ),
                      ),
                    ),
                  if (!isProcessing) ...[
                    Icon(
                      isActive ? Icons.remove : Icons.add,
                      color: isActive ? Colors.green : AppColors.secondary,
                      size: 16,
                    ),
                    const SizedBox(width: 4),
                    Text(
                      isActive ? 'Remove' : 'Add',
                      style: AppTextStyle.bodySmall.copyWith(
                        color: isActive ? Colors.green : AppColors.secondary,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  // Custom Assistance Tab
  Widget _buildCustomAssistanceTab() {
    if (_isLoadingCustom) {
      return const Center(child: CircularProgressIndicator());
    }

    final featureTitles = _featureKeys
        .map((feature) => _formatFeatureName(feature).toLowerCase())
        .toSet();
    final visiblePrompts = _customPrompts.where((prompt) {
      final title = (prompt['title'] ?? '').toString().trim().toLowerCase();
      return title.isNotEmpty && !featureTitles.contains(title);
    }).toList();

    return Column(
      children: [
        Expanded(
          child: visiblePrompts.isEmpty
              ? Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.psychology_outlined, size: 64, color: AppColors.grey.withOpacity(0.5)),
                      const SizedBox(height: 16),
                      Text(
                        'No custom assistance yet',
                        style: AppTextStyle.titleMedium.copyWith(color: AppColors.grey),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Create your first custom AI assistant',
                        style: AppTextStyle.bodySmall.copyWith(color: AppColors.grey),
                      ),
                    ],
                  ),
                )
              : ListView.builder(
                  padding: const EdgeInsets.all(16),
                  itemCount: visiblePrompts.length,
                  itemBuilder: (context, index) {
                    final prompt = visiblePrompts[index];
                    return _buildCustomPromptCard(
                      title: prompt['title'] ?? 'Custom',
                      description: prompt['prompt'] ?? '',
                      onRemove: () => _deleteCustomPrompt(prompt['title'] ?? ''),
                    );
                  },
                ),
        ),
        
        // Create Custom Assistance Button
        Padding(
          padding: const EdgeInsets.all(16),
          child: GestureDetector(
            onTap: _showCreateCustomAssistanceScreen,
            child: Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(vertical: 16),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [Color(0xFF7B2CBF), Color(0xFF5A189A)],
                  begin: Alignment.centerLeft,
                  end: Alignment.centerRight,
                ),
                borderRadius: BorderRadius.circular(30),
                boxShadow: [
                  BoxShadow(
                    color: AppColors.secondary.withOpacity(0.3),
                    blurRadius: 12,
                    offset: const Offset(0, 4),
                  ),
                ],
              ),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Icon(Icons.add, color: AppColors.white, size: 24),
                  const SizedBox(width: 8),
                  Text(
                    'Create Custom Assistance',
                    style: AppTextStyle.titleMedium.copyWith(
                      color: AppColors.white,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildCustomPromptCard({
    required String title,
    required String description,
    required VoidCallback onRemove,
  }) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppColors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.lightGrey, width: 1),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withOpacity(0.05),
            blurRadius: 10,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Row(
        children: [
          // Icon
            Container(
            width: 48,
            height: 48,
            decoration: BoxDecoration(
              color: AppColors.secondary.withOpacity(0.1),
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Center(
              child: Text('🎨', style: TextStyle(fontSize: 24)),
            ),
          ),
          const SizedBox(width: 16),

          // Title and Description
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: AppTextStyle.titleLarge.copyWith(
                    fontWeight: FontWeight.w700,
                    color: AppColors.black,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  description,
                  style: AppTextStyle.bodySmall.copyWith(color: AppColors.grey),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),

          const SizedBox(width: 12),

          // Remove Button
          GestureDetector(
            onTap: onRemove,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              decoration: BoxDecoration(
                color: Colors.green.withOpacity(0.1),
                borderRadius: BorderRadius.circular(20),
                border: Border.all(color: Colors.green, width: 1),
              ),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.remove, color: Colors.green, size: 16),
                  const SizedBox(width: 4),
                  Text(
                    'Remove',
                    style: AppTextStyle.bodySmall.copyWith(
                      color: Colors.green,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  void _showCreateCustomAssistanceScreen() {
    final TextEditingController promptController = TextEditingController();
    
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => Scaffold(
          backgroundColor: AppColors.white,
          appBar: AppBar(
            backgroundColor: AppColors.primary,
            elevation: 0,
            leading: IconButton(
              icon: const Icon(Icons.arrow_back, color: AppColors.white),
              onPressed: () => Navigator.pop(context),
            ),
            title: Text(
              'Create Custom Assistance',
              style: AppTextStyle.headlineMedium.copyWith(color: AppColors.white),
            ),
            centerTitle: false,
          ),
          body: SafeArea(
            child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Write Instructions or Prompt For AI:',
                  style: AppTextStyle.titleMedium.copyWith(
                    color: AppColors.black,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 8),
                GestureDetector(
                  onTap: () {
                    // Could show a library of example prompts
                  },
                  child: Text(
                    'Or Pick From Assistance Library',
                    style: AppTextStyle.bodyMedium.copyWith(
                      color: AppColors.secondary,
                      decoration: TextDecoration.underline,
                    ),
                  ),
                ),
                const SizedBox(height: 24),
                
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: AppColors.lightGrey.withOpacity(0.5),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Examples:',
                        style: AppTextStyle.bodyMedium.copyWith(
                          color: AppColors.grey,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        '1.Summarise the content in 50 words\n2.Add more hashtags in the content\n3.Write a instagram caption\n4.Rewrite in natural, human-like way',
                        style: AppTextStyle.bodySmall.copyWith(color: AppColors.grey),
                      ),
                    ],
                  ),
                ),
                
                const SizedBox(height: 24),
                
                Expanded(
                  child: Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: AppColors.lightGrey.withOpacity(0.3),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: TextField(
                      controller: promptController,
                      maxLines: null,
                      expands: true,
                      textAlignVertical: TextAlignVertical.top,
                      decoration: const InputDecoration(
                        border: InputBorder.none,
                        hintText: 'Write your custom AI instructions here...',
                      ),
                      style: AppTextStyle.bodyMedium.copyWith(color: AppColors.black),
                    ),
                  ),
                ),
                
                const SizedBox(height: 24),
                
                GestureDetector(
                  onTap: () {
                    if (promptController.text.trim().isEmpty) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(
                          content: Text('Please enter instructions'),
                          backgroundColor: Colors.orange,
                        ),
                      );
                      return;
                    }
                    Navigator.pop(context);
                    _showAssistanceNameDialog(promptController.text.trim());
                  },
                  child: Container(
                    width: double.infinity,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    decoration: BoxDecoration(
                      color: AppColors.grey.withOpacity(0.3),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      'Proceed',
                      style: AppTextStyle.titleMedium.copyWith(
                        color: AppColors.black,
                        fontWeight: FontWeight.w600,
                      ),
                      textAlign: TextAlign.center,
                    ),
                  ),
                ),
              ],
            ),
          )),
        ),
      ),
    );
  }

  void _showAssistanceNameDialog(String prompt) {
    final TextEditingController nameController = TextEditingController();
    
    showDialog(
      context: context,
      builder: (context) => Dialog(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Enter Assistance Name',
                style: AppTextStyle.titleLarge.copyWith(
                  color: AppColors.black,
                  fontWeight: FontWeight.w700,
                ),
              ),
              const SizedBox(height: 24),
              
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                decoration: BoxDecoration(
                  color: AppColors.lightGrey.withOpacity(0.5),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: TextField(
                  controller: nameController,
                  decoration: InputDecoration(
                    hintText: 'Assistance Name',
                    hintStyle: AppTextStyle.bodyMedium.copyWith(color: AppColors.grey),
                    border: InputBorder.none,
                  ),
                  style: AppTextStyle.bodyMedium.copyWith(color: AppColors.black),
                ),
              ),
              
              const SizedBox(height: 24),
              
              GestureDetector(
                onTap: () async {
                  if (nameController.text.trim().isEmpty) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(
                        content: Text('Please enter a name'),
                        backgroundColor: Colors.orange,
                      ),
                    );
                    return;
                  }
                  
                  Navigator.pop(context);
                  await _saveCustomAssistance(nameController.text.trim(), prompt);
                },
                child: Container(
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(vertical: 16),
                  decoration: BoxDecoration(
                    color: AppColors.grey.withOpacity(0.3),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    'Save',
                    style: AppTextStyle.titleMedium.copyWith(
                      color: AppColors.black,
                      fontWeight: FontWeight.w600,
                    ),
                    textAlign: TextAlign.center,
                  ),
              ),
            ),
          ],
          ),
        ),
      ),
    );
  }

  Future<void> _saveCustomAssistance(String name, String prompt) async {
    try {
      // Save to local (keyboard)
      await promptChannel.invokeMethod('addPrompt', {
        'category': 'assistant',
        'title': name,
        'prompt': prompt,
      });
      
      // Save to Firebase if user is logged in
      if (_currentUser != null) {
        await FirebaseFirestore.instance
            .collection('users')
            .doc(_currentUser!.uid)
            .collection('custom_assistants')
            .add({
          'title': name,
          'prompt': prompt,
          'timestamp': FieldValue.serverTimestamp(),
        });
        debugPrint('✅ Custom assistance saved to Firebase');
      } else {
        debugPrint('⚠️ User not logged in, skipping Firebase sync');
      }
      
      // Reload custom prompts
      await _loadCustomPrompts();
      
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('✅ Custom assistance "$name" created'),
          backgroundColor: Colors.green,
        ),
      );
      
      // Switch to Custom Assistance tab
      _tabController.animateTo(1);
    } catch (e) {
      debugPrint('Error saving custom assistance: $e');
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('❌ Error: $e'),
          backgroundColor: Colors.red,
        ),
      );
    }
  }

  Future<void> _deleteCustomPrompt(String title) async {
    try {
      // Delete from local (keyboard)
      await promptChannel.invokeMethod('removePrompt', {
        'category': 'assistant',
        'title': title,
      });
      
      // Delete from Firebase if user is logged in
      if (_currentUser != null) {
        final snapshot = await FirebaseFirestore.instance
            .collection('users')
            .doc(_currentUser!.uid)
            .collection('custom_assistants')
            .where('title', isEqualTo: title)
            .get();
        
        for (var doc in snapshot.docs) {
          await doc.reference.delete();
        }
        debugPrint('✅ Custom assistance deleted from Firebase');
      }
      
      // Reload custom prompts
      await _loadCustomPrompts();
      
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('✅ "$title" deleted'),
          backgroundColor: Colors.green,
        ),
      );
    } catch (e) {
      debugPrint('Error deleting custom prompt: $e');
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('❌ Error: $e'),
          backgroundColor: Colors.red,
        ),
      );
    }
  }

  Future<void> _onFeatureTap(String featureId) async {
    if (_processingFeatures.contains(featureId)) return;
    
    final bool isAdded = _activeFeatures.contains(featureId);
    final String method = isAdded ? 'removePrompt' : 'addPrompt';
    final String title = _formatFeatureName(featureId);
    final String prompt = _getPromptForFeature(featureId);

    setState(() {
      _processingFeatures.add(featureId);
    });

    var shouldToggleState = false;
    try {
      await promptChannel.invokeMethod(method, {
        'category': 'assistant',
        'title': title,
        'prompt': prompt,
      });
      shouldToggleState = true;
    } on PlatformException catch (e) {
      final isDeleteMiss = isAdded && e.code == 'DELETE_ERROR';
      if (!isDeleteMiss) {
        debugPrint('Error toggling feature $featureId: $e');
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('❌ Error: ${e.message ?? e.code}'),
              backgroundColor: Colors.red,
            ),
          );
        }
        return;
      }
      debugPrint('Feature $featureId not found in storage; treating delete as noop.');
      shouldToggleState = true;
    } finally {
      if (mounted) {
        setState(() {
          if (shouldToggleState) {
            if (isAdded) {
              _activeFeatures.remove(featureId);
            } else {
              _activeFeatures.add(featureId);
            }
          }
          _processingFeatures.remove(featureId);
        });
      }
    }

    if (!shouldToggleState) {
      return;
    }

    await Future.delayed(const Duration(milliseconds: 250));

    await _saveAIWritingFeatures();
  }
  
  Future<void> _saveAIWritingFeatures() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      
      // Save each feature state
      for (String feature in _featureKeys) {
        await prefs.setBool('flutter.ai_writing_$feature', _activeFeatures.contains(feature));
      }
      
      // Save to Firebase if logged in
      if (_currentUser != null) {
        await FirebaseFirestore.instance
            .collection('users')
            .doc(_currentUser!.uid)
            .set({
          'active_features': _activeFeatures.toList(),
          'last_updated': FieldValue.serverTimestamp(),
        }, SetOptions(merge: true));
      }
      
      debugPrint('🧠 AI writing features saved: $_activeFeatures');
      
      // Notify keyboard
      const platform = MethodChannel('ai_keyboard/config');
      await platform.invokeMethod('settingsChanged');
    } catch (e) {
      debugPrint('Error saving AI writing features: $e');
    }
  }
  
  String _getPromptForFeature(String feature) {
    switch (feature) {
        case 'humanise':
        return 'Rewrite this text to sound more human and natural while maintaining the original meaning.';
        case 'reply':
        return 'Generate a thoughtful and appropriate reply to this message.';
        case 'continue_writing':
        return 'Continue writing this text naturally and coherently.';
        case 'facebook_post':
        return 'Transform this into an engaging Facebook post with appropriate tone.';
        case 'instagram_caption':
        return 'Create an engaging Instagram caption from this text.';
        case 'phrase_to_emoji':
        return 'Convert relevant words and phrases in this text to appropriate emojis.';
        case 'summary':
        return 'Create a clear and concise summary of this text.';
      default:
        return 'Improve and enhance this text.';
    }
  }

  String _formatFeatureName(String feature) {
    return feature.split('_').map((word) => 
      word[0].toUpperCase() + word.substring(1)
    ).join(' ');
  }
}
