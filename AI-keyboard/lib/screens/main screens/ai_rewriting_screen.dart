import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:ai_keyboard/screens/main%20screens/ai_writing_assistance_screen.dart';

/// Main "Customise AI" screen with navigation to Grammar, Tone, and AI Writing sections
class AiRewritingScreen extends StatefulWidget {
  const AiRewritingScreen({super.key});

  @override
  State<AiRewritingScreen> createState() => _AiRewritingScreenState();
}

class _AiRewritingScreenState extends State<AiRewritingScreen> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: AppColors.white),
          onPressed: () => Navigator.pop(context),
        ),
        title: Text(
          'Customise AI',
          style: AppTextStyle.headlineMedium.copyWith(color: AppColors.white),
        ),
        centerTitle: false,
        backgroundColor: AppColors.primary,
        elevation: 0,
        actions: [
          Stack(
            children: [
              const Icon(Icons.notifications, color: AppColors.white, size: 24),
              Positioned(
                right: 0,
                top: 0,
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
          const SizedBox(width: 16),
        ],
      ),
      backgroundColor: AppColors.white,
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            const SizedBox(height: 8),
            
            // Word Tone Card
            _buildNavigationCard(
              icon: Icons.auto_awesome,
              iconColor: AppColors.secondary,
              title: 'Word Tone',
              subtitle: 'Add custom tones to Word Tone',
              onTap: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (context) => const CustomTonesScreen(),
                  ),
                );
              },
            ),
            
            const SizedBox(height: 16),
            
            // Fix Grammar Card
            _buildNavigationCard(
              icon: Icons.spellcheck,
              iconColor: AppColors.secondary,
              title: 'Fix Grammar',
              subtitle: 'Add your custom instructions for grammar fixes',
              onTap: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (context) => const CustomGrammarScreen(),
                  ),
                );
              },
            ),
            
            const SizedBox(height: 16),
            
            // AI Writing Assistance Card
            _buildNavigationCard(
              icon: Icons.psychology,
              iconColor: AppColors.secondary,
              title: 'AI Writing Assistance',
              subtitle: 'Add your custom AI Assistants',
              onTap: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (context) => const AIWritingAssistanceScreen(),
                  ),
                );
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildNavigationCard({
    required IconData icon,
    required Color iconColor,
    required String title,
    required String subtitle,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
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
            // Icon Container
            Container(
              width: 48,
              height: 48,
              decoration: BoxDecoration(
                color: iconColor.withOpacity(0.15),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Icon(
                icon,
                color: iconColor,
                size: 24,
              ),
            ),
            const SizedBox(width: 16),
            
            // Text Content
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    title,
                    style: AppTextStyle.titleLarge.copyWith(
                      color: AppColors.black,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    subtitle,
                    style: AppTextStyle.bodySmall.copyWith(
                      color: AppColors.grey,
                    ),
                  ),
                ],
              ),
            ),
            
            // Arrow Icon
            const Icon(
              Icons.arrow_forward_ios,
              color: AppColors.grey,
              size: 18,
            ),
          ],
        ),
      ),
    );
  }
}

/// Custom Tones Screen - Manage tone prompts
class CustomTonesScreen extends StatefulWidget {
  const CustomTonesScreen({super.key});

  @override
  State<CustomTonesScreen> createState() => _CustomTonesScreenState();
}

class _CustomTonesScreenState extends State<CustomTonesScreen> {
  static const promptChannel = MethodChannel('ai_keyboard/prompts');
  List<Map<String, dynamic>> _tonePrompts = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadTonePrompts();
  }

  Future<void> _loadTonePrompts() async {
    try {
      final result = await promptChannel.invokeMethod('getPrompts', {'category': 'tone'});
      if (result is List) {
        setState(() {
          _tonePrompts = result.map((e) => Map<String, dynamic>.from(e)).toList();
          _isLoading = false;
        });
      }
    } catch (e) {
      debugPrint('Error loading tone prompts: $e');
      setState(() => _isLoading = false);
    }
  }

  Future<void> _deletePrompt(String title) async {
    try {
      await promptChannel.invokeMethod('deletePrompt', {
        'category': 'tone',
        'title': title,
      });
      _loadTonePrompts();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('✅ Tone deleted'), backgroundColor: Colors.green),
      );
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('❌ Error: $e'), backgroundColor: Colors.red),
      );
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
          'Custom Tones',
          style: AppTextStyle.headlineMedium.copyWith(color: AppColors.white),
        ),
        centerTitle: false,
        backgroundColor: AppColors.primary,
        elevation: 0,
      ),
      backgroundColor: AppColors.white,
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: [
                // List of tone prompts
                Expanded(
                  child: _tonePrompts.isEmpty
                      ? Center(
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(Icons.sentiment_neutral, size: 64, color: AppColors.grey.withOpacity(0.5)),
                              const SizedBox(height: 16),
                              Text(
                                'No custom tones yet',
                                style: AppTextStyle.titleMedium.copyWith(color: AppColors.grey),
                              ),
                              const SizedBox(height: 8),
                              Text(
                                'Tap + to create your first tone',
                                style: AppTextStyle.bodySmall.copyWith(color: AppColors.grey),
                              ),
                            ],
                          ),
                        )
                      : ListView.builder(
                          padding: const EdgeInsets.all(16),
                          itemCount: _tonePrompts.length,
                          itemBuilder: (context, index) {
                            final prompt = _tonePrompts[index];
                            return Container(
                              margin: const EdgeInsets.only(bottom: 12),
                              padding: const EdgeInsets.all(16),
                              decoration: BoxDecoration(
                                color: AppColors.lightGrey.withOpacity(0.5),
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: Row(
                                children: [
                                  Expanded(
                                    child: Text(
                                      prompt['title'] ?? '',
                                      style: AppTextStyle.titleMedium.copyWith(
                                        color: AppColors.black,
                                        fontWeight: FontWeight.w600,
                                      ),
                                    ),
                                  ),
                                  IconButton(
                                    icon: const Icon(Icons.delete, color: Colors.red),
                                    onPressed: () => _deletePrompt(prompt['title'] ?? ''),
                                  ),
                                ],
                              ),
                            );
                          },
                        ),
                ),
                
                // Bottom section
                Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    children: [
                      // Create button
                      GestureDetector(
                        onTap: () => _showCreateToneDialog(),
                        child: Container(
                          width: double.infinity,
                          padding: const EdgeInsets.symmetric(vertical: 16),
                          decoration: BoxDecoration(
                            color: AppColors.secondary,
                            borderRadius: BorderRadius.circular(30),
                          ),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              const Icon(Icons.add, color: AppColors.white, size: 24),
                              const SizedBox(width: 8),
                              Text(
                                'Create Custom Tone',
                                style: AppTextStyle.titleMedium.copyWith(
                                  color: AppColors.white,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                      const SizedBox(height: 16),
                      Text(
                        'Try keyboard here!',
                        style: AppTextStyle.bodySmall.copyWith(color: AppColors.grey),
                      ),
                    ],
                  ),
                ),
              ],
            ),
    );
  }

  void _showCreateToneDialog() {
    final TextEditingController toneNameController = TextEditingController();
    
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (BuildContext context) {
        final bottomInset = MediaQuery.of(context).viewInsets.bottom;
        return Padding(
          padding: EdgeInsets.only(bottom: bottomInset),
          child: SingleChildScrollView(
            child: Container(
              decoration: const BoxDecoration(
                color: AppColors.white,
                borderRadius: BorderRadius.only(
                  topLeft: Radius.circular(24),
                  topRight: Radius.circular(24),
                ),
              ),
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Handle bar
                  Center(
                    child: Container(
                      width: 40,
                      height: 4,
                      decoration: BoxDecoration(
                        color: AppColors.grey.withOpacity(0.3),
                        borderRadius: BorderRadius.circular(2),
                      ),
                    ),
                  ),
                  const SizedBox(height: 24),
                  
                  // Title
                  Text(
                    'Create Custom Tone',
                    style: AppTextStyle.headlineMedium.copyWith(
                      color: AppColors.black,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  const SizedBox(height: 24),
                  
                  // Input field label
                  Text(
                    'Enter Tone name',
                    style: AppTextStyle.titleMedium.copyWith(
                      color: AppColors.black,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 12),
                  
                  // Input field
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                    decoration: BoxDecoration(
                      color: AppColors.lightGrey,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: TextField(
                      controller: toneNameController,
                      decoration: InputDecoration(
                        hintText: 'Eg: Angry, Sarcastic',
                        hintStyle: AppTextStyle.bodyMedium.copyWith(color: AppColors.grey),
                        border: InputBorder.none,
                      ),
                      style: AppTextStyle.bodyMedium.copyWith(color: AppColors.black),
                    ),
                  ),
                  const SizedBox(height: 16),
                  
                  // Info text
                  Text(
                    'Custom Tones will show up in the Word Tone section',
                    style: AppTextStyle.bodySmall.copyWith(color: AppColors.grey),
                  ),
                  const SizedBox(height: 24),
                  
                  // Add button
                  GestureDetector(
                    onTap: () async {
                      if (toneNameController.text.trim().isEmpty) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(
                            content: Text('Please enter a tone name'),
                            backgroundColor: Colors.orange,
                          ),
                        );
                        return;
                      }
                      
                      try {
                        final toneName = toneNameController.text.trim();
                        final prompt = 'Make this text $toneName in tone';
                        
                        await promptChannel.invokeMethod('savePrompt', {
                          'category': 'tone',
                          'title': toneName,
                          'prompt': prompt,
                        });
                        
                        Navigator.pop(context);
                        _loadTonePrompts();
                        
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            content: Text('✅ Tone "$toneName" created'),
                            backgroundColor: Colors.green,
                          ),
                        );
                      } catch (e) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            content: Text('❌ Error: $e'),
                            backgroundColor: Colors.red,
                          ),
                        );
                      }
                    },
                    child: Container(
                      width: double.infinity,
                      padding: const EdgeInsets.symmetric(vertical: 16),
                      decoration: BoxDecoration(
                        color: AppColors.grey.withOpacity(0.3),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Text(
                        'Add Tone',
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
      },
    );
  }
}

/// Custom Grammar Screen - Manage grammar prompts
class CustomGrammarScreen extends StatefulWidget {
  const CustomGrammarScreen({super.key});

  @override
  State<CustomGrammarScreen> createState() => _CustomGrammarScreenState();
}

class _CustomGrammarScreenState extends State<CustomGrammarScreen> {
  static const promptChannel = MethodChannel('ai_keyboard/prompts');
  List<Map<String, dynamic>> _grammarPrompts = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadGrammarPrompts();
  }

  Future<void> _loadGrammarPrompts() async {
    try {
      final result = await promptChannel.invokeMethod('getPrompts', {'category': 'grammar'});
      if (result is List) {
        setState(() {
          _grammarPrompts = result.map((e) => Map<String, dynamic>.from(e)).toList();
          _isLoading = false;
        });
      }
    } catch (e) {
      debugPrint('Error loading grammar prompts: $e');
      setState(() => _isLoading = false);
    }
  }

  Future<void> _deletePrompt(String title) async {
    try {
      await promptChannel.invokeMethod('deletePrompt', {
        'category': 'grammar',
        'title': title,
      });
      _loadGrammarPrompts();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('✅ Grammar prompt deleted'), backgroundColor: Colors.green),
      );
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('❌ Error: $e'), backgroundColor: Colors.red),
      );
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
          'Custom Grammar',
          style: AppTextStyle.headlineMedium.copyWith(color: AppColors.white),
        ),
        centerTitle: false,
        backgroundColor: AppColors.primary,
        elevation: 0,
      ),
      backgroundColor: AppColors.white,
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: [
                // List of grammar prompts
                Expanded(
                  child: _grammarPrompts.isEmpty
                      ? Center(
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Icon(Icons.edit_note, size: 64, color: AppColors.grey.withOpacity(0.5)),
                              const SizedBox(height: 16),
                              Text(
                                'No custom grammar prompts yet',
                                style: AppTextStyle.titleMedium.copyWith(color: AppColors.grey),
                              ),
                              const SizedBox(height: 8),
                              Text(
                                'Tap + to create your first prompt',
                                style: AppTextStyle.bodySmall.copyWith(color: AppColors.grey),
                              ),
                            ],
                          ),
                        )
                      : ListView.builder(
                          padding: const EdgeInsets.all(16),
                          itemCount: _grammarPrompts.length,
                          itemBuilder: (context, index) {
                            final prompt = _grammarPrompts[index];
                            return Container(
                              margin: const EdgeInsets.only(bottom: 12),
                              padding: const EdgeInsets.all(16),
                              decoration: BoxDecoration(
                                color: AppColors.lightGrey.withOpacity(0.5),
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: Row(
                                children: [
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text(
                                          prompt['title'] ?? '',
                                          style: AppTextStyle.titleMedium.copyWith(
                                            color: AppColors.black,
                                            fontWeight: FontWeight.w600,
                                          ),
                                        ),
                                        const SizedBox(height: 4),
                                        Text(
                                          prompt['prompt'] ?? '',
                                          style: AppTextStyle.bodySmall.copyWith(color: AppColors.grey),
                                          maxLines: 2,
                                          overflow: TextOverflow.ellipsis,
                                        ),
                                      ],
                                    ),
                                  ),
                                  IconButton(
                                    icon: const Icon(Icons.delete, color: Colors.red),
                                    onPressed: () => _deletePrompt(prompt['title'] ?? ''),
                                  ),
                                ],
                              ),
                            );
                          },
                        ),
                ),
                
                // Bottom section
                Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    children: [
                      // Create button
                      GestureDetector(
                        onTap: () => _showCreateGrammarDialog(),
                        child: Container(
                          width: double.infinity,
                          padding: const EdgeInsets.symmetric(vertical: 16),
                          decoration: BoxDecoration(
                            color: AppColors.secondary,
                            borderRadius: BorderRadius.circular(30),
                          ),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              const Icon(Icons.add, color: AppColors.white, size: 24),
                              const SizedBox(width: 8),
                              Text(
                                'Create Grammar Prompt',
                                style: AppTextStyle.titleMedium.copyWith(
                                  color: AppColors.white,
                                  fontWeight: FontWeight.w700,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                      const SizedBox(height: 16),
                      Text(
                        'Try keyboard here!',
                        style: AppTextStyle.bodySmall.copyWith(color: AppColors.grey),
                      ),
                    ],
                  ),
                ),
              ],
            ),
    );
  }

  void _showCreateGrammarDialog() {
    final TextEditingController titleController = TextEditingController();
    final TextEditingController promptController = TextEditingController();
    
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (BuildContext context) {
        return Container(
          decoration: const BoxDecoration(
            color: AppColors.white,
            borderRadius: BorderRadius.only(
              topLeft: Radius.circular(24),
              topRight: Radius.circular(24),
            ),
          ),
          padding: const EdgeInsets.all(24),
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Handle bar
                Center(
                  child: Container(
                    width: 40,
                    height: 4,
                    decoration: BoxDecoration(
                      color: AppColors.grey.withOpacity(0.3),
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
                const SizedBox(height: 24),
                
                // Title
                Text(
                  'Create Grammar Prompt',
                  style: AppTextStyle.headlineMedium.copyWith(
                    color: AppColors.black,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 24),
                
                // Title field label
                Text(
                  'Prompt Title',
                  style: AppTextStyle.titleMedium.copyWith(
                    color: AppColors.black,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 12),
                
                // Title input field
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                  decoration: BoxDecoration(
                    color: AppColors.lightGrey,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: TextField(
                    controller: titleController,
                    decoration: InputDecoration(
                      hintText: 'Eg: Professional Fix',
                      hintStyle: AppTextStyle.bodyMedium.copyWith(color: AppColors.grey),
                      border: InputBorder.none,
                    ),
                    style: AppTextStyle.bodyMedium.copyWith(color: AppColors.black),
                  ),
                ),
                const SizedBox(height: 20),
                
                // Prompt field label
                Text(
                  'Grammar Instructions',
                  style: AppTextStyle.titleMedium.copyWith(
                    color: AppColors.black,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 12),
                
                // Prompt input field
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  decoration: BoxDecoration(
                    color: AppColors.lightGrey,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: TextField(
                    controller: promptController,
                    maxLines: 4,
                    decoration: InputDecoration(
                      hintText: 'Eg: Fix grammar and spelling while maintaining a professional tone',
                      hintStyle: AppTextStyle.bodyMedium.copyWith(color: AppColors.grey),
                      border: InputBorder.none,
                    ),
                    style: AppTextStyle.bodyMedium.copyWith(color: AppColors.black),
                  ),
                ),
                const SizedBox(height: 16),
                
                // Info text
                Text(
                  'Custom Grammar prompts will show up in the Fix Grammar section',
                  style: AppTextStyle.bodySmall.copyWith(color: AppColors.grey),
                ),
                const SizedBox(height: 24),
                
                // Add button
                GestureDetector(
                  onTap: () async {
                    if (titleController.text.trim().isEmpty || promptController.text.trim().isEmpty) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(
                          content: Text('Please fill in all fields'),
                          backgroundColor: Colors.orange,
                        ),
                      );
                      return;
                    }
                    
                    try {
                      await promptChannel.invokeMethod('savePrompt', {
                        'category': 'grammar',
                        'title': titleController.text.trim(),
                        'prompt': promptController.text.trim(),
                      });
                      
                      Navigator.pop(context);
                      _loadGrammarPrompts();
                      
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text('✅ Grammar prompt "${titleController.text.trim()}" created'),
                          backgroundColor: Colors.green,
                        ),
                      );
                    } catch (e) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text('❌ Error: $e'),
                          backgroundColor: Colors.red,
                        ),
                      );
                    }
                  },
                  child: Container(
                    width: double.infinity,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    decoration: BoxDecoration(
                      color: AppColors.secondary,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      'Add Grammar Prompt',
                      style: AppTextStyle.titleMedium.copyWith(
                        color: AppColors.white,
                        fontWeight: FontWeight.w600,
                      ),
                      textAlign: TextAlign.center,
                    ),
                  ),
                ),
                
                // Add bottom padding for keyboard
                SizedBox(height: MediaQuery.of(context).viewInsets.bottom),
              ],
            ),
          ),
        );
      },
    );
  }
}
