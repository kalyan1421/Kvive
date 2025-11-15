import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import 'features/auth/admin_signup_screen.dart';
import 'features/logins/login_logs_section.dart';
import 'features/notifications/notification_section.dart';
import 'features/storage/storage_section.dart';

class AdminApp extends StatelessWidget {
  const AdminApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'KVive Admin',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xff0066ff),
          brightness: Brightness.light,
        ),
        useMaterial3: true,
        inputDecorationTheme: const InputDecorationTheme(
          border: OutlineInputBorder(),
        ),
      ),
      home: const AdminGate(),
    );
  }
}

/// Screen shown while verifying admin access (handles timing after signup)
class _AdminVerificationScreen extends StatefulWidget {
  final String userId;

  const _AdminVerificationScreen({required this.userId});

  @override
  State<_AdminVerificationScreen> createState() => _AdminVerificationScreenState();
}

class _AdminVerificationScreenState extends State<_AdminVerificationScreen> {
  bool _hasChecked = false;

  @override
  void initState() {
    super.initState();
    _verifyAdminAccess();
  }

  Future<void> _verifyAdminAccess() async {
    // Wait longer for Firestore to propagate and signup to complete
    // Signup screen needs time to write the admin document
    await Future.delayed(const Duration(milliseconds: 3000));

    if (!mounted) return;

    // Re-check if admin document exists with retries
    bool found = false;
    for (int i = 0; i < 5; i++) {
      final adminDoc = await FirebaseFirestore.instance
          .collection('adminUsers')
          .doc(widget.userId)
          .get();

      if (!mounted) return;

      if (adminDoc.exists) {
        found = true;
        break;
      }
      
      // Wait before retrying
      if (i < 4) {
        await Future.delayed(const Duration(milliseconds: 500));
      }
    }

    if (!mounted) return;

    if (!found) {
      // Still doesn't exist after multiple retries, sign out
      await FirebaseAuth.instance.signOut();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'You are not authorized to access the admin panel.',
            ),
            backgroundColor: Colors.red,
          ),
        );
      }
    } else {
      // Document exists now, set flag to trigger rebuild
      setState(() {
        _hasChecked = true;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_hasChecked) {
      // Admin verified, show dashboard directly
      return const AdminDashboard();
    }

    return Scaffold(
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const CircularProgressIndicator(),
            const SizedBox(height: 16),
            Text(
              'Verifying admin access...',
              style: Theme.of(context).textTheme.bodyLarge,
            ),
          ],
        ),
      ),
    );
  }
}

class AdminGate extends StatelessWidget {
  const AdminGate({super.key});

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<User?>(
      stream: FirebaseAuth.instance.authStateChanges(),
      builder: (context, authSnapshot) {
        if (authSnapshot.connectionState == ConnectionState.waiting) {
          return const Scaffold(
            body: Center(child: CircularProgressIndicator()),
          );
        }
        if (authSnapshot.hasError) {
          return Scaffold(
            body: Center(
              child: Text('Authentication error: ${authSnapshot.error}'),
            ),
          );
        }

        // If user is not authenticated, check if admin users exist
        if (authSnapshot.data == null) {
          return StreamBuilder<QuerySnapshot>(
            stream: FirebaseFirestore.instance
                .collection('adminUsers')
                .limit(1)
                .snapshots(),
            builder: (context, adminSnapshot) {
              if (adminSnapshot.connectionState == ConnectionState.waiting) {
                return const Scaffold(
                  body: Center(child: CircularProgressIndicator()),
                );
              }

              // If no admin users exist, show signup screen
              if (adminSnapshot.data?.docs.isEmpty ?? true) {
                return const AdminSignupScreen();
              }

              // Admin users exist, show sign-in screen
              return const SignInPanel();
            },
          );
        }

        // User is authenticated, check if they are admin
        final user = authSnapshot.data!;
        return StreamBuilder<DocumentSnapshot>(
          stream: FirebaseFirestore.instance
              .collection('adminUsers')
              .doc(user.uid)
              .snapshots(),
          builder: (context, adminDocSnapshot) {
            if (adminDocSnapshot.connectionState == ConnectionState.waiting) {
              return const Scaffold(
                body: Center(child: CircularProgressIndicator()),
              );
            }

            // Check if document exists
            final adminDoc = adminDocSnapshot.data;
            final exists = adminDoc?.exists ?? false;

            // If user is not in adminUsers collection, wait a bit and re-check
            // This handles the case where signup just happened and Firestore is still propagating
            if (!exists) {
              // Show loading while checking
              return _AdminVerificationScreen(userId: user.uid);
            }

            // User is admin, show dashboard
            return const AdminDashboard();
          },
        );
      },
    );
  }
}

class SignInPanel extends StatefulWidget {
  const SignInPanel({super.key});

  @override
  State<SignInPanel> createState() => _SignInPanelState();
}

class _SignInPanelState extends State<SignInPanel> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _isSubmitting = false;
  bool _showPassword = false;

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _handleSubmit({required bool createAccount}) async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    setState(() => _isSubmitting = true);
    final email = _emailController.text.trim();
    final password = _passwordController.text.trim();
    try {
      if (createAccount) {
        await FirebaseAuth.instance.createUserWithEmailAndPassword(
          email: email,
          password: password,
        );
      } else {
        await FirebaseAuth.instance.signInWithEmailAndPassword(
          email: email,
          password: password,
        );
      }
    } on FirebaseAuthException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(error.message ?? 'Authentication failed')),
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
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 420),
          child: Card(
            margin: const EdgeInsets.all(24),
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Form(
                key: _formKey,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      'KVive Admin',
                      style: Theme.of(context).textTheme.headlineMedium,
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Sign in with a Firebase Authentication admin account.',
                      textAlign: TextAlign.center,
                      style: Theme.of(context).textTheme.bodyMedium,
                    ),
                    const SizedBox(height: 24),
                    TextFormField(
                      controller: _emailController,
                      decoration: const InputDecoration(
                        labelText: 'Work email',
                        prefixIcon: Icon(Icons.mail_outlined),
                      ),
                      keyboardType: TextInputType.emailAddress,
                      validator: (value) {
                        if (value == null || value.trim().isEmpty) {
                          return 'Enter an email';
                        }
                        if (!value.contains('@')) {
                          return 'Invalid email';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _passwordController,
                      obscureText: !_showPassword,
                      decoration: InputDecoration(
                        labelText: 'Password',
                        prefixIcon: const Icon(Icons.lock_outline),
                        suffixIcon: IconButton(
                          icon: Icon(
                            _showPassword
                                ? Icons.visibility_off
                                : Icons.visibility,
                          ),
                          onPressed: () => setState(
                            () => _showPassword = !_showPassword,
                          ),
                        ),
                      ),
                      validator: (value) {
                        if (value == null || value.isEmpty) {
                          return 'Enter a password';
                        }
                        if (value.length < 8) {
                          return 'Use at least 8 characters';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 24),
                    Row(
                      children: [
                        Expanded(
                          child: FilledButton(
                            onPressed:
                                _isSubmitting ? null : () => _handleSubmit(createAccount: false),
                            child: _isSubmitting
                                ? const SizedBox(
                                    height: 20,
                                    width: 20,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                      valueColor: AlwaysStoppedAnimation<Color>(
                                        Colors.white,
                                      ),
                                    ),
                                  )
                                : const Text('Sign in'),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    TextButton(
                      onPressed: _isSubmitting
                          ? null
                          : () => _handleSubmit(createAccount: true),
                      child: const Text('Create a new admin account'),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

enum DashboardSection {
  overview,
  logins,
  storage,
  notifications,
  settings,
}

class AdminDashboard extends StatefulWidget {
  const AdminDashboard({super.key});

  @override
  State<AdminDashboard> createState() => _AdminDashboardState();
}

class _AdminDashboardState extends State<AdminDashboard> {
  DashboardSection _section = DashboardSection.overview;

  void _handleSignOut() {
    FirebaseAuth.instance.signOut();
  }

  Widget _sectionWidget(DashboardSection section) {
    switch (section) {
      case DashboardSection.overview:
        return const OverviewSection();
      case DashboardSection.logins:
        return const LoginLogsSection();
      case DashboardSection.storage:
        return const StorageSection();
      case DashboardSection.notifications:
        return const NotificationSection();
      case DashboardSection.settings:
        return const SettingsSection();
    }
  }

  @override
  Widget build(BuildContext context) {
    final isWide = MediaQuery.of(context).size.width > 900;
    return Scaffold(
      appBar: AppBar(
        title: const Text('KVive Admin'),
        actions: [
          IconButton(
            tooltip: 'Sign out',
            onPressed: _handleSignOut,
            icon: const Icon(Icons.logout),
          ),
        ],
      ),
      body: Row(
        children: [
          NavigationRail(
            extended: isWide,
            selectedIndex: DashboardSection.values.indexOf(_section),
            onDestinationSelected: (value) {
              setState(() => _section = DashboardSection.values[value]);
            },
            labelType: isWide ? NavigationRailLabelType.none : NavigationRailLabelType.selected,
            destinations: const [
              NavigationRailDestination(
                icon: Icon(Icons.dashboard_outlined),
                selectedIcon: Icon(Icons.dashboard),
                label: Text('Overview'),
              ),
              NavigationRailDestination(
                icon: Icon(Icons.people_outline),
                selectedIcon: Icon(Icons.people),
                label: Text('Logins'),
              ),
              NavigationRailDestination(
                icon: Icon(Icons.folder_copy_outlined),
                selectedIcon: Icon(Icons.folder_copy),
                label: Text('Storage'),
              ),
              NavigationRailDestination(
                icon: Icon(Icons.campaign_outlined),
                selectedIcon: Icon(Icons.campaign),
                label: Text('Notifications'),
              ),
              NavigationRailDestination(
                icon: Icon(Icons.settings_outlined),
                selectedIcon: Icon(Icons.settings),
                label: Text('Settings'),
              ),
            ],
          ),
          Expanded(
            child: AnimatedSwitcher(
              duration: const Duration(milliseconds: 250),
              child: _sectionWidget(_section),
            ),
          ),
        ],
      ),
    );
  }
}

class OverviewSection extends StatelessWidget {
  const OverviewSection({super.key});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: ListView(
        children: [
          Wrap(
            runSpacing: 16,
            spacing: 16,
            children: const [
              SizedBox(
                width: 320,
                child: CollectionCountCard(
                  label: 'Registered users',
                  collection: 'users',
                ),
              ),
              SizedBox(
                width: 320,
                child: CollectionCountCard(
                  label: 'Recent logins (24h)',
                  collection: 'userSessions',
                  timeframeHours: 24,
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),
          const ProjectInfoCard(),
        ],
      ),
    );
  }
}

class CollectionCountCard extends StatelessWidget {
  const CollectionCountCard({
    required this.label,
    required this.collection,
    super.key,
    this.timeframeHours,
  });

  final String label;
  final String collection;
  final int? timeframeHours;

  Future<int?> _loadCount() async {
    try {
      if (timeframeHours == null) {
        final snapshot =
            await FirebaseFirestore.instance.collection(collection).count().get();
        return snapshot.count;
      }
      final since = Timestamp.fromDate(
        DateTime.now().subtract(Duration(hours: timeframeHours!)),
      );
      final snapshot = await FirebaseFirestore.instance
          .collection(collection)
          .where('lastLoginAt', isGreaterThanOrEqualTo: since)
          .count()
          .get();
      return snapshot.count;
    } catch (error) {
      return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: FutureBuilder<int?>(
          future: _loadCount(),
          builder: (context, snapshot) {
            final textTheme = Theme.of(context).textTheme;
            if (snapshot.connectionState == ConnectionState.waiting) {
              return Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(label, style: textTheme.titleMedium),
                    const SizedBox(height: 8),
                    const CircularProgressIndicator(),
                  ],
                ),
              );
            }
            if (snapshot.data == null) {
              return Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(label, style: textTheme.titleMedium),
                  const SizedBox(height: 8),
                  Text(
                    '—',
                    style: textTheme.displaySmall,
                  ),
                  const SizedBox(height: 8),
                  const Text('Unable to read count (check Firestore rules).'),
                ],
              );
            }
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label, style: textTheme.titleMedium),
                const SizedBox(height: 8),
                Text(
                  NumberFormat.compact().format(snapshot.data),
                  style: textTheme.displaySmall,
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}

class ProjectInfoCard extends StatelessWidget {
  const ProjectInfoCard({super.key});

  @override
  Widget build(BuildContext context) {
    final rows = const [
      ('Project name', 'AIkeyboard'),
      ('Project ID', 'aikeyboard-18ed9'),
      ('Project number', '621863637081'),
      ('Storage bucket', 'gs://aikeyboard-18ed9.firebasestorage.app'),
      ('Public name', 'project-621863637081'),
      ('Environment', 'Unspecified'),
    ];
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Firebase project overview',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 16),
            ...rows.map(
              (row) => Padding(
                padding: const EdgeInsets.symmetric(vertical: 6),
                child: Row(
                  children: [
                    SizedBox(
                      width: 200,
                      child: Text(
                        row.$1,
                        style: Theme.of(context)
                            .textTheme
                            .bodyMedium
                            ?.copyWith(color: Colors.grey[600]),
                      ),
                    ),
                    Expanded(
                      child: Text(
                        row.$2,
                        style: Theme.of(context).textTheme.bodyLarge,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class SettingsSection extends StatelessWidget {
  const SettingsSection({super.key});

  @override
  Widget build(BuildContext context) {
    final currentUser = FirebaseAuth.instance.currentUser;
    return ListView(
      padding: const EdgeInsets.all(24),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Admin Account',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 16),
                if (currentUser != null) ...[
                  ListTile(
                    leading: const Icon(Icons.person),
                    title: const Text('Email'),
                    subtitle: Text(currentUser.email ?? 'No email'),
                  ),
                  ListTile(
                    leading: const Icon(Icons.badge),
                    title: const Text('User ID'),
                    subtitle: SelectableText(currentUser.uid),
                  ),
                  StreamBuilder<DocumentSnapshot>(
                    stream: FirebaseFirestore.instance
                        .collection('adminUsers')
                        .doc(currentUser.uid)
                        .snapshots(),
                    builder: (context, snapshot) {
                      if (snapshot.hasData && snapshot.data?.exists == true) {
                        final data = snapshot.data!.data() as Map<String, dynamic>?;
                        final isSuperAdmin = data?['isSuperAdmin'] ?? false;
                        final role = data?['role'] ?? 'admin';
                        return ListTile(
                          leading: Icon(
                            isSuperAdmin ? Icons.admin_panel_settings : Icons.person,
                            color: isSuperAdmin ? Colors.amber : null,
                          ),
                          title: const Text('Role'),
                          subtitle: Text(
                            isSuperAdmin ? 'Super Admin' : role.toString(),
                          ),
                        );
                      }
                      return const SizedBox.shrink();
                    },
                  ),
                ],
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Firebase Project Info',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 16),
                const SelectableText(
                  'Project: aikeyboard-18ed9\n'
                  'Project ID: aikeyboard-18ed9\n'
                  'Project Number: 621863637081\n'
                  'Storage: gs://aikeyboard-18ed9.firebasestorage.app',
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 16),
        Card(
          child: ListTile(
            leading: const Icon(Icons.security),
            title: const Text('Admin Permissions'),
            subtitle: const Text(
              'As an admin, you have full read/write access to all Firestore collections '
              'and Firebase Storage. You can manage users, devices, notifications, and more.',
            ),
          ),
        ),
      ],
    );
  }
}
