import 'package:ai_keyboard/utils/appassets.dart';
import 'package:ai_keyboard/utils/apptextstyle.dart';
import 'package:flutter/material.dart';
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:intl/intl.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../services/notification_service.dart';

class NotificationScreen extends StatefulWidget {
  const NotificationScreen({Key? key}) : super(key: key);

  @override
  State<NotificationScreen> createState() => _NotificationScreenState();
}

class _NotificationScreenState extends State<NotificationScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  int _selectedTabIndex = 0;
  String? _deviceToken;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _tabController.addListener(() {
      setState(() {
        _selectedTabIndex = _tabController.index;
      });
    });
    _loadDeviceToken();
  }

  Future<void> _loadDeviceToken() async {
    final prefs = await SharedPreferences.getInstance();
    final token = prefs.getString('fcm_token_saved');
    setState(() {
      _deviceToken = token;
    });
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
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
          'Notification',
          style: AppTextStyle.headlineMedium.copyWith(color: AppColors.white),
        ),
        centerTitle: true,
      ),
      body: Column(
        children: [
          // Custom Tab Bar
          _buildCustomTabBar(),

          // Tab Content
          Expanded(
            child: TabBarView(
              controller: _tabController,
              children: [_buildRecentActivityTab(), _buildUnreadTab()],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCustomTabBar() {
    return Container(
      color: AppColors.white,
      child: Row(
        children: [
          Expanded(
            child: _buildTabButton(
              'Recent activity',
              0,
              () => _tabController.animateTo(0),
            ),
          ),
          Expanded(
            child: _buildTabButton(
              'Unread',
              1,
              () => _tabController.animateTo(1),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTabButton(String text, int index, VoidCallback onTap) {
    bool isSelected = _selectedTabIndex == index;

    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 16),
        decoration: BoxDecoration(
          border: Border(
            bottom: BorderSide(
              color: isSelected ? AppColors.secondary : Colors.transparent,
              width: 2,
            ),
          ),
        ),
        child: Text(
          text,
          textAlign: TextAlign.center,
          style: AppTextStyle.titleMedium.copyWith(
            color: isSelected ? AppColors.secondary : AppColors.grey,
            fontWeight: isSelected ? FontWeight.w600 : FontWeight.w500,
          ),
        ),
      ),
    );
  }

  Widget _buildRecentActivityTab() {
    if (_deviceToken == null) {
      return const Center(
        child: CircularProgressIndicator(),
      );
    }

    return StreamBuilder<QuerySnapshot<Map<String, dynamic>>>(
      stream: FirebaseFirestore.instance
          .collection('userNotifications')
          .doc(_deviceToken)
          .collection('notifications')
          .orderBy('receivedAt', descending: true)
          .snapshots(),
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Center(child: CircularProgressIndicator());
        }

        if (snapshot.hasError) {
          return Center(
            child: Text('Error: ${snapshot.error}'),
          );
        }

        final notifications = snapshot.data?.docs ?? [];
        if (notifications.isEmpty) {
          return Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(Icons.notifications_none, size: 64, color: AppColors.grey),
                const SizedBox(height: 16),
                Text(
                  'No notifications yet',
                  style: AppTextStyle.titleMedium.copyWith(color: AppColors.grey),
                ),
              ],
            ),
          );
        }

        final grouped = _groupNotificationsByDate(notifications);
        return SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: grouped.entries.map((entry) {
              return _buildNotificationSection(entry.key, entry.value);
            }).toList(),
          ),
        );
      },
    );
  }

  Widget _buildUnreadTab() {
    if (_deviceToken == null) {
      return const Center(
        child: CircularProgressIndicator(),
      );
    }

    return StreamBuilder<QuerySnapshot<Map<String, dynamic>>>(
      stream: FirebaseFirestore.instance
          .collection('userNotifications')
          .doc(_deviceToken)
          .collection('notifications')
          .where('isRead', isEqualTo: false)
          .orderBy('receivedAt', descending: true)
          .snapshots(),
      builder: (context, snapshot) {
        if (snapshot.connectionState == ConnectionState.waiting) {
          return const Center(child: CircularProgressIndicator());
        }

        if (snapshot.hasError) {
          return Center(
            child: Text('Error: ${snapshot.error}'),
          );
        }

        final notifications = snapshot.data?.docs ?? [];
        if (notifications.isEmpty) {
          return Center(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(Icons.check_circle_outline, size: 64, color: AppColors.grey),
                const SizedBox(height: 16),
                Text(
                  'No unread notifications',
                  style: AppTextStyle.titleMedium.copyWith(color: AppColors.grey),
                ),
              ],
            ),
          );
        }

        final grouped = _groupNotificationsByDate(notifications);
        return SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: grouped.entries.map((entry) {
              return _buildNotificationSection(entry.key, entry.value);
            }).toList(),
          ),
        );
      },
    );
  }

  Map<String, List<DocumentSnapshot<Map<String, dynamic>>>> _groupNotificationsByDate(
    List<DocumentSnapshot<Map<String, dynamic>>> notifications,
  ) {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final yesterday = today.subtract(const Duration(days: 1));

    final Map<String, List<DocumentSnapshot<Map<String, dynamic>>>> grouped = {
      'Today': [],
      'Yesterday': [],
      'Older': [],
    };

    for (var notification in notifications) {
      final data = notification.data();
      final receivedAt = data?['receivedAt'] as Timestamp?;
      
      if (receivedAt == null) continue;

      final date = receivedAt.toDate();
      final notificationDate = DateTime(date.year, date.month, date.day);

      if (notificationDate == today) {
        grouped['Today']!.add(notification);
      } else if (notificationDate == yesterday) {
        grouped['Yesterday']!.add(notification);
      } else {
        grouped['Older']!.add(notification);
      }
    }

    // Remove empty groups
    grouped.removeWhere((key, value) => value.isEmpty);
    return grouped;
  }

  Widget _buildNotificationSection(
    String title,
    List<DocumentSnapshot<Map<String, dynamic>>> notifications,
  ) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.all(16),
          child: Text(
            title,
            style: AppTextStyle.titleLarge.copyWith(
              color: AppColors.grey,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
        ...notifications.map(
          (notification) => _buildNotificationCardFromDoc(notification),
        ),
      ],
    );
  }

  Widget _buildNotificationCardFromDoc(DocumentSnapshot<Map<String, dynamic>> doc) {
    final data = doc.data();
    if (data == null) return const SizedBox.shrink();

    final title = data['title'] ?? 'Notification';
    final body = data['body'] ?? '';
    final isRead = data['isRead'] ?? false;
    final link = data['link'] as String?;
    final receivedAt = data['receivedAt'] as Timestamp?;
    final iconType = NotificationService.getIconType(data);

    return GestureDetector(
      onTap: () {
        if (!isRead) {
          NotificationService.markAsRead(doc.id);
        }
        if (link != null && link.isNotEmpty) {
          // Handle deep link navigation
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Opening: $link')),
          );
        }
      },
      child: Container(
        margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          color: AppColors.white,
          borderRadius: BorderRadius.circular(12),
          border: !isRead
              ? Border(bottom: BorderSide(color: AppColors.secondary, width: 3))
              : null,
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.05),
              blurRadius: 8,
              offset: const Offset(0, 2),
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _buildNotificationIcon(iconType),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.start,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        title,
                        style: AppTextStyle.titleMedium.copyWith(
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        body,
                        style: AppTextStyle.bodyMedium.copyWith(
                          color: AppColors.grey,
                        ),
                      ),
                      if (link != null && link.isNotEmpty) ...[
                        const SizedBox(height: 16),
                        _buildActionButton('Open', link),
                      ],
                    ],
                  ),
                ),
                Text(
                  _formatTime(receivedAt),
                  style: AppTextStyle.bodySmall.copyWith(
                    color: AppColors.primary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
          ],
        ),
      ),
    );
  }

  String _formatTime(Timestamp? timestamp) {
    if (timestamp == null) return 'Just now';

    final date = timestamp.toDate();
    final now = DateTime.now();
    final difference = now.difference(date);

    if (difference.inMinutes < 1) {
      return 'Just now';
    } else if (difference.inMinutes < 60) {
      return '${difference.inMinutes}min ago';
    } else if (difference.inHours < 24) {
      return '${difference.inHours}h ago';
    } else if (difference.inDays < 7) {
      return '${difference.inDays}d ago';
    } else {
      return DateFormat('MMM d, y').format(date);
    }
  }

  Widget _buildNotificationIcon(String iconType) {
    switch (iconType) {
      case 'premium':
        return Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            color: AppColors.secondary.withOpacity(0.2),
            borderRadius: BorderRadius.circular(20),
          ),
          child: const Icon(
            Icons.notifications,
            color: AppColors.secondary,
            size: 20,
          ),
        );
      case 'profile':
        return Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            color: AppColors.tertiary.withOpacity(0.2),
            borderRadius: BorderRadius.circular(20),
          ),
          child: const Icon(Icons.person, color: AppColors.tertiary, size: 20),
        );
      case 'theme':
        return Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            color: AppColors.primary.withOpacity(0.2),
            borderRadius: BorderRadius.circular(20),
          ),
          child: const Icon(Icons.palette, color: AppColors.primary, size: 20),
        );
      default:
        return Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            color: AppColors.grey.withOpacity(0.2),
            borderRadius: BorderRadius.circular(20),
          ),
          child: const Icon(
            Icons.notifications,
            color: AppColors.grey,
            size: 20,
          ),
        );
    }
  }

  Widget _buildActionButton(String actionText, String? link) {
    return SizedBox(
      child: ElevatedButton(
        onPressed: () {
          if (link != null && link.isNotEmpty) {
            // Handle deep link navigation
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text('Opening: $link')),
            );
            // You can add actual navigation logic here
          } else {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text('$actionText pressed')),
            );
          }
        },
        style: ElevatedButton.styleFrom(
          elevation: 0,
          backgroundColor: Color(0xffFFF4DE),
          foregroundColor: AppColors.secondary,
          padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 16),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(8),
            side: BorderSide(color: AppColors.secondary),
          ),
        ),
        child: Text(
          actionText,
          style: AppTextStyle.buttonPrimary.copyWith(
            color: AppColors.secondary,
          ),
        ),
      ),
    );
  }

}
