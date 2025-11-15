import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';

class AdminSignupScreen extends StatefulWidget {
  const AdminSignupScreen({super.key});

  @override
  State<AdminSignupScreen> createState() => _AdminSignupScreenState();
}

class _AdminSignupScreenState extends State<AdminSignupScreen> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();
  final _nameController = TextEditingController();
  bool _isSubmitting = false;
  bool _showPassword = false;
  bool _showConfirmPassword = false;

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    _confirmPasswordController.dispose();
    _nameController.dispose();
    super.dispose();
  }

  Future<void> _handleSignup() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }

    setState(() => _isSubmitting = true);

    final email = _emailController.text.trim();
    final password = _passwordController.text.trim();
    final name = _nameController.text.trim();

    try {
      // Check if any admin users exist
      final adminUsersSnapshot = await FirebaseFirestore.instance
          .collection('adminUsers')
          .limit(1)
          .get();

      if (adminUsersSnapshot.docs.isNotEmpty) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Admin accounts already exist. Please sign in instead.',
            ),
            backgroundColor: Colors.orange,
          ),
        );
        setState(() => _isSubmitting = false);
        return;
      }

      // Create Firebase Auth user
      final userCredential = await FirebaseAuth.instance
          .createUserWithEmailAndPassword(
        email: email,
        password: password,
      );

      final user = userCredential.user;
      if (user == null) {
        throw Exception('User creation failed');
      }

      // Update user display name (optional, may fail if not configured)
      try {
        await user.updateDisplayName(name);
        await user.reload();
      } catch (e) {
        debugPrint('Note: Could not update display name: $e');
        // Continue anyway, display name is optional
      }

      // Add to adminUsers collection
      // IMPORTANT: This must complete successfully before AdminGate's verification runs
      try {
        // Verify user is still authenticated before proceeding
        final currentUser = FirebaseAuth.instance.currentUser;
        if (currentUser == null || currentUser.uid != user.uid) {
          throw Exception('User authentication lost during signup process');
        }
        
        // Write to Firestore with retry logic
        bool writeSuccess = false;
        int retries = 0;
        const maxRetries = 5;
        
        while (!writeSuccess && retries < maxRetries) {
          try {
            // Check user is still authenticated before each retry
            final checkUser = FirebaseAuth.instance.currentUser;
            if (checkUser == null || checkUser.uid != user.uid) {
              throw Exception('User was signed out during Firestore write');
            }
            
            debugPrint('Attempting to write admin document (attempt ${retries + 1}/$maxRetries)');
            
            await FirebaseFirestore.instance
                .collection('adminUsers')
                .doc(user.uid)
                .set({
              'email': email,
              'name': name,
              'createdAt': FieldValue.serverTimestamp(),
              'isSuperAdmin': true, // First admin is super admin
              'role': 'super_admin',
            });
            
            debugPrint('Admin document written, waiting for propagation...');
            
            // Wait for Firestore to propagate
            await Future.delayed(const Duration(milliseconds: 1000));
            
            // Verify the document was created
            final adminDoc = await FirebaseFirestore.instance
                .collection('adminUsers')
                .doc(user.uid)
                .get();
            
            if (adminDoc.exists) {
              debugPrint('Admin document verified successfully');
              writeSuccess = true;
            } else {
              debugPrint('Admin document not found, retrying...');
              retries++;
              if (retries < maxRetries) {
                await Future.delayed(const Duration(milliseconds: 500));
              }
            }
          } catch (e) {
            debugPrint('Error writing admin document: $e');
            retries++;
            if (retries < maxRetries) {
              await Future.delayed(const Duration(milliseconds: 500));
            } else {
              rethrow;
            }
          }
        }
        
        if (!writeSuccess) {
          throw Exception('Failed to create admin document in Firestore after $maxRetries attempts');
        }

        if (!mounted) return;

        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Admin account created successfully!'),
            backgroundColor: Colors.green,
            duration: Duration(seconds: 2),
          ),
        );

        // Wait a bit more to ensure AdminGate sees the document
        await Future.delayed(const Duration(milliseconds: 500));

        // User will be automatically signed in and redirected to dashboard
        // The AdminGate will detect the admin document and show the dashboard
      } catch (firestoreError) {
        // If Firestore write fails, delete the auth user to keep things clean
        try {
          await user.delete();
        } catch (_) {
          // Ignore deletion errors
        }
        if (!mounted) return;
        throw Exception('Failed to save admin data: $firestoreError');
      }
    } on FirebaseAuthException catch (error) {
      if (!mounted) return;
      String message = 'Signup failed';
      switch (error.code) {
        case 'weak-password':
          message = 'Password is too weak. Use at least 8 characters.';
          break;
        case 'email-already-in-use':
          message = 'This email is already registered. Please sign in instead.';
          // Don't reset form, let them try signing in
          break;
        case 'invalid-email':
          message = 'Invalid email address.';
          break;
        default:
          message = error.message ?? 'Signup failed';
      }
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(message),
          backgroundColor: Colors.red,
          duration: const Duration(seconds: 4),
        ),
      );
    } catch (error) {
      if (!mounted) return;
      debugPrint('Signup error: $error');
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Error: ${error.toString()}'),
          backgroundColor: Colors.red,
          duration: const Duration(seconds: 4),
        ),
      );
    } finally {
      if (mounted) {
        setState(() => _isSubmitting = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [
              Theme.of(context).colorScheme.primary,
              Theme.of(context).colorScheme.secondary,
            ],
          ),
        ),
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 480),
                child: Card(
                  elevation: 8,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(16),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.all(32),
                    child: Form(
                      key: _formKey,
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          // Icon
                          Container(
                            padding: const EdgeInsets.all(16),
                            decoration: BoxDecoration(
                              color: Theme.of(context)
                                  .colorScheme
                                  .primary
                                  .withOpacity(0.1),
                              shape: BoxShape.circle,
                            ),
                            child: Icon(
                              Icons.admin_panel_settings,
                              size: 48,
                              color: Theme.of(context).colorScheme.primary,
                            ),
                          ),
                          const SizedBox(height: 24),
                          // Title
                          Text(
                            'Create First Admin Account',
                            textAlign: TextAlign.center,
                            style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                                  fontWeight: FontWeight.bold,
                                ),
                          ),
                          const SizedBox(height: 8),
                          Text(
                            'Set up the first administrator account for KVive Admin Panel',
                            textAlign: TextAlign.center,
                            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                                  color: Colors.grey[600],
                                ),
                          ),
                          const SizedBox(height: 32),
                          // Name field
                          TextFormField(
                            controller: _nameController,
                            decoration: const InputDecoration(
                              labelText: 'Full Name',
                              prefixIcon: Icon(Icons.person_outline),
                              hintText: 'Enter your full name',
                            ),
                            textCapitalization: TextCapitalization.words,
                            validator: (value) {
                              if (value == null || value.trim().isEmpty) {
                                return 'Enter your name';
                              }
                              if (value.trim().length < 2) {
                                return 'Name must be at least 2 characters';
                              }
                              return null;
                            },
                          ),
                          const SizedBox(height: 16),
                          // Email field
                          TextFormField(
                            controller: _emailController,
                            decoration: const InputDecoration(
                              labelText: 'Email Address',
                              prefixIcon: Icon(Icons.mail_outline),
                              hintText: 'admin@example.com',
                            ),
                            keyboardType: TextInputType.emailAddress,
                            autocorrect: false,
                            validator: (value) {
                              if (value == null || value.trim().isEmpty) {
                                return 'Enter an email address';
                              }
                              if (!value.contains('@') || !value.contains('.')) {
                                return 'Enter a valid email address';
                              }
                              return null;
                            },
                          ),
                          const SizedBox(height: 16),
                          // Password field
                          TextFormField(
                            controller: _passwordController,
                            obscureText: !_showPassword,
                            decoration: InputDecoration(
                              labelText: 'Password',
                              prefixIcon: const Icon(Icons.lock_outline),
                              hintText: 'At least 8 characters',
                              suffixIcon: IconButton(
                                icon: Icon(
                                  _showPassword
                                      ? Icons.visibility_off
                                      : Icons.visibility,
                                ),
                                onPressed: () {
                                  setState(() => _showPassword = !_showPassword);
                                },
                              ),
                            ),
                            validator: (value) {
                              if (value == null || value.isEmpty) {
                                return 'Enter a password';
                              }
                              if (value.length < 8) {
                                return 'Password must be at least 8 characters';
                              }
                              return null;
                            },
                          ),
                          const SizedBox(height: 16),
                          // Confirm password field
                          TextFormField(
                            controller: _confirmPasswordController,
                            obscureText: !_showConfirmPassword,
                            decoration: InputDecoration(
                              labelText: 'Confirm Password',
                              prefixIcon: const Icon(Icons.lock_outline),
                              suffixIcon: IconButton(
                                icon: Icon(
                                  _showConfirmPassword
                                      ? Icons.visibility_off
                                      : Icons.visibility,
                                ),
                                onPressed: () {
                                  setState(() =>
                                      _showConfirmPassword = !_showConfirmPassword);
                                },
                              ),
                            ),
                            validator: (value) {
                              if (value == null || value.isEmpty) {
                                return 'Confirm your password';
                              }
                              if (value != _passwordController.text) {
                                return 'Passwords do not match';
                              }
                              return null;
                            },
                          ),
                          const SizedBox(height: 32),
                          // Submit button
                          FilledButton(
                            onPressed: _isSubmitting ? null : _handleSignup,
                            style: FilledButton.styleFrom(
                              padding: const EdgeInsets.symmetric(vertical: 16),
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(8),
                              ),
                            ),
                            child: _isSubmitting
                                ? const SizedBox(
                                    height: 20,
                                    width: 20,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                      valueColor:
                                          AlwaysStoppedAnimation<Color>(Colors.white),
                                    ),
                                  )
                                : const Text(
                                    'Create Admin Account',
                                    style: TextStyle(
                                      fontSize: 16,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                          ),
                          const SizedBox(height: 16),
                          // Info text
                          Container(
                            padding: const EdgeInsets.all(12),
                            decoration: BoxDecoration(
                              color: Colors.blue.withOpacity(0.1),
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: Row(
                              children: [
                                Icon(
                                  Icons.info_outline,
                                  size: 20,
                                  color: Colors.blue[700],
                                ),
                                const SizedBox(width: 8),
                                Expanded(
                                  child: Text(
                                    'This will create the first admin account. '
                                    'You can create additional admin accounts after signing in.',
                                    style: TextStyle(
                                      fontSize: 12,
                                      color: Colors.blue[900],
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
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

