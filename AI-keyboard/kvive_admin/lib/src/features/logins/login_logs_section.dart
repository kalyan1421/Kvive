import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

const _loginCollection = 'userSessions';

class LoginLogsSection extends StatefulWidget {
  const LoginLogsSection({super.key});

  @override
  State<LoginLogsSection> createState() => _LoginLogsSectionState();
}

class _LoginLogsSectionState extends State<LoginLogsSection> {
  final _searchController = TextEditingController();

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Stream<List<LoginLog>> _watchLogs() {
    final query = FirebaseFirestore.instance
        .collection(_loginCollection)
        .orderBy('lastLoginAt', descending: true)
        .limit(200);
    return query.snapshots().map(
      (snapshot) => snapshot.docs.map(LoginLog.fromDoc).toList(),
    );
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.all(24),
      children: [
        Card(
          clipBehavior: Clip.antiAlias,
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text(
                      'Recent user logins',
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                    const Spacer(),
                    IconButton(
                      tooltip: 'Refresh',
                      onPressed: () => setState(() {}),
                      icon: const Icon(Icons.refresh),
                    ),
                  ],
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: _searchController,
                  decoration: const InputDecoration(
                    labelText: 'Search by email, user ID or device',
                    prefixIcon: Icon(Icons.search),
                  ),
                  onChanged: (_) => setState(() {}),
                ),
                const SizedBox(height: 16),
                SizedBox(
                  height: 500,
                  child: StreamBuilder<List<LoginLog>>(
                    stream: _watchLogs(),
                    builder: (context, snapshot) {
                      if (snapshot.connectionState == ConnectionState.waiting) {
                        return const Center(child: CircularProgressIndicator());
                      }
                      if (snapshot.hasError) {
                        return Center(
                          child: Text(
                            'Unable to load login logs: ${snapshot.error}',
                          ),
                        );
                      }
                      final logs = snapshot.data ?? [];
                      final filtered = logs.where(
                        (log) => log.matchesQuery(_searchController.text),
                      );
                      if (filtered.isEmpty) {
                        return const Center(
                          child: Text('No login activity yet.'),
                        );
                      }
                      final rows = filtered
                          .map(
                            (log) => DataRow(
                              cells: [
                                DataCell(Text(log.email)),
                                DataCell(Text(log.userId)),
                                DataCell(Text(log.platform ?? '—')),
                                DataCell(Text(log.ipAddress ?? '—')),
                                DataCell(Text(log.formattedTimestamp)),
                              ],
                            ),
                          )
                          .toList();
                      return Scrollbar(
                        thumbVisibility: true,
                        child: SingleChildScrollView(
                          scrollDirection: Axis.horizontal,
                          child: DataTable(
                            showCheckboxColumn: false,
                            headingRowColor: WidgetStateProperty.all(
                              Theme.of(context)
                                  .colorScheme
                                  .surfaceContainerHighest,
                            ),
                            columns: const [
                              DataColumn(label: Text('Email')),
                              DataColumn(label: Text('User ID')),
                              DataColumn(label: Text('Platform')),
                              DataColumn(label: Text('IP address')),
                              DataColumn(label: Text('Last login')),
                            ],
                            rows: rows,
                          ),
                        ),
                      );
                    },
                  ),
                ),
                const SizedBox(height: 12),
                const Text(
                  'Logs are read from the `userSessions` collection. '
                  'Each document should contain email, userId, platform, '
                  'ipAddress and lastLoginAt fields.',
                  style: TextStyle(color: Colors.black54),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class LoginLog {
  const LoginLog({
    required this.id,
    required this.userId,
    required this.email,
    this.platform,
    this.ipAddress,
    this.timestamp,
  });

  final String id;
  final String userId;
  final String email;
  final String? platform;
  final String? ipAddress;
  final DateTime? timestamp;

  factory LoginLog.fromDoc(DocumentSnapshot<Map<String, dynamic>> doc) {
    final data = doc.data() ?? {};
    final timestamp = data['lastLoginAt'];
    DateTime? parsed;
    if (timestamp is Timestamp) {
      parsed = timestamp.toDate();
    } else if (timestamp is DateTime) {
      parsed = timestamp;
    }
    return LoginLog(
      id: doc.id,
      userId: (data['userId'] ?? 'unknown').toString(),
      email: (data['email'] ?? 'unknown').toString(),
      platform: data['platform']?.toString(),
      ipAddress: data['ipAddress']?.toString(),
      timestamp: parsed,
    );
  }

  bool matchesQuery(String query) {
    if (query.isEmpty) return true;
    final lower = query.toLowerCase();
    return email.toLowerCase().contains(lower) ||
        userId.toLowerCase().contains(lower) ||
        (platform?.toLowerCase().contains(lower) ?? false) ||
        (ipAddress?.toLowerCase().contains(lower) ?? false);
  }

  String get formattedTimestamp {
    if (timestamp == null) return '—';
    return DateFormat.yMMMd().add_jm().format(timestamp!.toLocal());
  }
}
