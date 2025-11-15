import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../services/fcm_notification_service.dart';

const _notificationsCollection = 'broadcastNotifications';

class NotificationSection extends StatefulWidget {
  const NotificationSection({super.key});

  @override
  State<NotificationSection> createState() => _NotificationSectionState();
}

class _NotificationSectionState extends State<NotificationSection> {
  final _formKey = GlobalKey<FormState>();
  final _titleController = TextEditingController();
  final _bodyController = TextEditingController();
  final _linkController = TextEditingController();
  bool _isSending = false;
  int _deviceCount = 0;

  @override
  void initState() {
    super.initState();
    _loadDeviceCount();
  }

  Future<void> _loadDeviceCount() async {
    final count = await FCMNotificationService.getDeviceCount();
    if (mounted) {
      setState(() => _deviceCount = count);
    }
  }

  @override
  void dispose() {
    _titleController.dispose();
    _bodyController.dispose();
    _linkController.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) {
      return;
    }
    setState(() => _isSending = true);
    try {
      final result = await FCMNotificationService.sendToAllDevices(
        title: _titleController.text.trim(),
        body: _bodyController.text.trim(),
        link: _linkController.text.trim().isEmpty
            ? null
            : _linkController.text.trim(),
      );

      if (mounted) {
        _formKey.currentState!.reset();
        _titleController.clear();
        _bodyController.clear();
        _linkController.clear();
        
        if (result['success'] == true) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(result['message'] ?? 'Notification queued for delivery.'),
              backgroundColor: Colors.green,
            ),
          );
          // Refresh device count
          _loadDeviceCount();
        } else {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(result['message'] ?? 'Unable to send notification.'),
              backgroundColor: Colors.red,
            ),
          );
        }
      }
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Unable to send: $error'),
            backgroundColor: Colors.red,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isSending = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(24),
      children: [
        Card(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(
                      child: Text(
                        'Broadcast notification',
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                    ),
                    if (_deviceCount > 0)
                      Chip(
                        avatar: const Icon(Icons.phone_android, size: 18),
                        label: Text('$_deviceCount devices'),
                      ),
                  ],
                ),
                const SizedBox(height: 16),
                Form(
                  key: _formKey,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      TextFormField(
                        controller: _titleController,
                        decoration: const InputDecoration(
                          labelText: 'Title',
                        ),
                        validator: (value) {
                          if (value == null || value.trim().isEmpty) {
                            return 'Title is required';
                          }
                          return null;
                        },
                      ),
                      const SizedBox(height: 16),
                      TextFormField(
                        controller: _bodyController,
                        decoration: const InputDecoration(
                          labelText: 'Message',
                        ),
                        maxLines: 4,
                        validator: (value) {
                          if (value == null || value.trim().isEmpty) {
                            return 'Message is required';
                          }
                          return null;
                        },
                      ),
                      const SizedBox(height: 16),
                      TextFormField(
                        controller: _linkController,
                        decoration: const InputDecoration(
                          labelText: 'Deep link (optional)',
                        ),
                      ),
                      const SizedBox(height: 16),
                      FilledButton.icon(
                        onPressed: _isSending ? null : _submit,
                        icon: const Icon(Icons.send),
                        label: _isSending
                            ? const Text('Sending…')
                            : const Text('Send to all users'),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        _deviceCount > 0
                            ? 'Notification will be sent to $_deviceCount registered devices. '
                                'Make sure your Cloud Function is set up to process notifications '
                                'from the `broadcastNotifications` collection.'
                            : 'No devices registered yet. Devices will register their FCM tokens '
                                'in the `devices` collection when the app starts.',
                        style: TextStyle(color: Colors.grey[600]),
                      ),
                    ],
                  ),
                ),
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
                  'Recently sent',
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: 16),
                SizedBox(
                  height: 400,
                  child: StreamBuilder<QuerySnapshot<Map<String, dynamic>>>(
                    stream: FirebaseFirestore.instance
                        .collection(_notificationsCollection)
                        .orderBy('createdAt', descending: true)
                        .limit(20)
                        .snapshots(),
                    builder: (context, snapshot) {
                      if (snapshot.connectionState == ConnectionState.waiting) {
                        return const Center(child: CircularProgressIndicator());
                      }
                      if (snapshot.hasError) {
                        return Center(
                          child: Text('Unable to load notifications: ${snapshot.error}'),
                        );
                      }
                      final docs = snapshot.data?.docs ?? [];
                      if (docs.isEmpty) {
                        return const Center(
                          child: Text('No notifications yet.'),
                        );
                      }
                      return ListView.builder(
                        itemCount: docs.length,
                        itemBuilder: (context, index) {
                          final data = docs[index].data();
                          final createdAt = data['createdAt'];
                          final title = data['title']?.toString() ?? 'Untitled';
                          final body = data['body']?.toString() ?? '';
                          final link = data['link']?.toString();
                          DateTime? timestamp;
                          if (createdAt is Timestamp) {
                            timestamp = createdAt.toDate();
                          }
                          return ListTile(
                            leading: const Icon(Icons.campaign),
                            title: Text(title),
                            subtitle: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(body),
                                if (link != null && link.isNotEmpty)
                                  Text(
                                    link,
                                    style: const TextStyle(color: Colors.blue),
                                  ),
                                Text(
                                  timestamp == null
                                      ? 'Pending time'
                                      : DateFormat.yMMMd().add_jm().format(
                                            timestamp.toLocal(),
                                          ),
                                ),
                              ],
                            ),
                          );
                        },
                      );
                    },
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
