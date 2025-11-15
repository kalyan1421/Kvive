import 'package:file_picker/file_picker.dart';
import 'package:firebase_storage/firebase_storage.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

class StorageSection extends StatefulWidget {
  const StorageSection({super.key});

  @override
  State<StorageSection> createState() => _StorageSectionState();
}

class _StorageSectionState extends State<StorageSection> {
  final _service = StorageService();
  late Future<List<StorageEntry>> _entriesFuture;
  String _currentPath = '';
  bool _isUploading = false;

  @override
  void initState() {
    super.initState();
    _entriesFuture = _service.listEntries(_currentPath);
  }

  Future<void> _reload() async {
    setState(() {
      _entriesFuture = _service.listEntries(_currentPath);
    });
  }

  Future<void> _openFolder(StorageEntry entry) async {
    if (!entry.isFolder) return;
    setState(() {
      _currentPath = entry.fullPath;
      _entriesFuture = _service.listEntries(_currentPath);
    });
  }

  String? get _parentPath {
    if (_currentPath.isEmpty) return null;
    final parts = _currentPath.split('/')..removeWhere((segment) => segment.isEmpty);
    if (parts.isEmpty) {
      return '';
    }
    parts.removeLast();
    return parts.join('/');
  }

  Future<void> _goBack() async {
    final parent = _parentPath;
    if (parent == null) return;
    setState(() {
      _currentPath = parent;
      _entriesFuture = _service.listEntries(_currentPath);
    });
  }

  Future<void> _handleUpload() async {
    setState(() => _isUploading = true);
    try {
      final result = await FilePicker.platform.pickFiles(
        withData: true,
        allowMultiple: false,
      );
      if (result == null || result.files.isEmpty) return;
      final file = result.files.single;
      await _service.uploadFile(
        destinationPath: _currentPath,
        file: file,
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Uploaded ${file.name}')),
        );
      }
      await _reload();
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Upload failed: $error')),
        );
      }
    } finally {
      if (mounted) {
        setState(() => _isUploading = false);
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
                    Text(
                      'Firebase Storage',
                      style: Theme.of(context).textTheme.titleLarge,
                    ),
                    const Spacer(),
                    IconButton(
                      tooltip: 'Refresh',
                      onPressed: _reload,
                      icon: const Icon(Icons.refresh),
                    ),
                    const SizedBox(width: 8),
                    FilledButton.icon(
                      onPressed: _isUploading ? null : _handleUpload,
                      icon: _isUploading
                          ? const SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.file_upload_outlined),
                      label: const Text('Upload file'),
                    ),
                  ],
                ),
                const SizedBox(height: 8),
                Text(
                  'Bucket: gs://aikeyboard-18ed9.firebasestorage.app',
                  style: Theme.of(context).textTheme.bodyMedium,
                ),
                const SizedBox(height: 16),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: _buildBreadcrumbs(),
                ),
                const SizedBox(height: 16),
                SizedBox(
                  height: 500,
                  child: FutureBuilder<List<StorageEntry>>(
                    future: _entriesFuture,
                    builder: (context, snapshot) {
                      if (snapshot.connectionState == ConnectionState.waiting) {
                        return const Center(child: CircularProgressIndicator());
                      }
                      if (snapshot.hasError) {
                        return Center(
                          child: Text('Unable to list storage: ${snapshot.error}'),
                        );
                      }
                      final entries = snapshot.data ?? [];
                      if (entries.isEmpty) {
                        return const Center(
                          child: Text('Folder is empty.'),
                        );
                      }
                      return Scrollbar(
                        thumbVisibility: true,
                        child: SingleChildScrollView(
                          scrollDirection: Axis.horizontal,
                          child: DataTable(
                            columns: const [
                              DataColumn(label: Text('Name')),
                              DataColumn(label: Text('Size')),
                              DataColumn(label: Text('Type')),
                              DataColumn(label: Text('Last modified')),
                            ],
                            rows: entries.map((entry) {
                              return DataRow(
                                cells: [
                                  DataCell(
                                    Row(
                                      children: [
                                        Icon(
                                          entry.isFolder
                                              ? Icons.folder_outlined
                                              : Icons.insert_drive_file_outlined,
                                        ),
                                        const SizedBox(width: 8),
                                        entry.isFolder
                                            ? TextButton(
                                                onPressed: () => _openFolder(entry),
                                                child: Text(entry.name),
                                              )
                                            : Text(entry.name),
                                      ],
                                    ),
                                  ),
                                  DataCell(Text(entry.sizeLabel)),
                                  DataCell(Text(entry.typeLabel)),
                                  DataCell(Text(entry.updatedLabel)),
                                ],
                              );
                            }).toList(),
                          ),
                        ),
                      );
                    },
                  ),
                ),
                const SizedBox(height: 12),
                const Text(
                  'Folders are automatically discovered (Fonts/, dictionaries/, metadata/, stickers/, transliteration/, etc.).',
                  style: TextStyle(color: Colors.black54),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  List<Widget> _buildBreadcrumbs() {
    final crumbs = <Widget>[];
    crumbs.add(
      ActionChip(
        label: const Text('/'),
        onPressed: _currentPath.isEmpty
            ? null
            : () {
                setState(() {
                  _currentPath = '';
                  _entriesFuture = _service.listEntries(_currentPath);
                });
              },
      ),
    );
    if (_currentPath.isEmpty) {
      return crumbs;
    }
    final segments = _currentPath.split('/')..removeWhere((segment) => segment.isEmpty);
    var pathAccumulator = '';
    for (final segment in segments) {
      pathAccumulator = pathAccumulator.isEmpty ? segment : '$pathAccumulator/$segment';
      crumbs.add(
        ActionChip(
          label: Text(segment),
          onPressed: () {
            setState(() {
              _currentPath = pathAccumulator;
              _entriesFuture = _service.listEntries(_currentPath);
            });
          },
        ),
      );
    }
    if (_parentPath != null) {
      crumbs.add(
        TextButton.icon(
          onPressed: _goBack,
          icon: const Icon(Icons.arrow_upward),
          label: const Text('Up'),
        ),
      );
    }
    return crumbs;
  }
}

class StorageService {
  final FirebaseStorage _storage = FirebaseStorage.instance;

  Future<List<StorageEntry>> listEntries(String path) async {
    final normalizedPath = path.trim();
    final reference =
        normalizedPath.isEmpty ? _storage.ref() : _storage.ref(normalizedPath);
    final listing = await reference.listAll();

    final folderEntries = listing.prefixes
        .map(
          (ref) => StorageEntry(
            name: ref.name,
            fullPath: ref.fullPath,
            isFolder: true,
          ),
        )
        .toList();

    final fileEntries = await Future.wait(
      listing.items.map((ref) async {
        final metadata = await ref.getMetadata();
        return StorageEntry(
          name: ref.name,
          fullPath: ref.fullPath,
          isFolder: false,
          sizeInBytes: metadata.size,
          updated: metadata.updated,
          contentType: metadata.contentType,
        );
      }),
    );

    final combined = [...folderEntries, ...fileEntries];
    combined.sort((a, b) {
      if (a.isFolder && !b.isFolder) return -1;
      if (!a.isFolder && b.isFolder) return 1;
      return a.name.toLowerCase().compareTo(b.name.toLowerCase());
    });
    return combined;
  }

  Future<void> uploadFile({
    required String destinationPath,
    required PlatformFile file,
  }) async {
    final bytes = file.bytes;
    if (bytes == null) {
      throw const FormatException('Unable to read bytes from the selected file.');
    }
    final sanitizedDestination = destinationPath
        .split('/')
        .where((segment) => segment.isNotEmpty)
        .join('/');
    final targetPath = sanitizedDestination.isEmpty
        ? file.name
        : '$sanitizedDestination/${file.name}';
    final ref = _storage.ref(targetPath);
    final metadata = SettableMetadata(
      contentType: _inferContentType(file.extension),
    );
    await ref.putData(bytes, metadata);
  }
}

class StorageEntry {
  StorageEntry({
    required this.name,
    required this.fullPath,
    required this.isFolder,
    this.sizeInBytes,
    this.updated,
    this.contentType,
  });

  final String name;
  final String fullPath;
  final bool isFolder;
  final int? sizeInBytes;
  final DateTime? updated;
  final String? contentType;

  String get sizeLabel {
    if (isFolder || sizeInBytes == null) return '—';
    return _formatBytes(sizeInBytes!);
  }

  String get typeLabel => isFolder
      ? 'Folder'
      : (contentType ?? 'Binary');

  String get updatedLabel {
    if (updated == null) return '—';
    return DateFormat.yMMMd().add_jm().format(updated!.toLocal());
  }
}

String _formatBytes(int bytes, [int decimals = 1]) {
  if (bytes <= 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  double value = bytes.toDouble();
  var unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex++;
  }
  final fractionDigits = unitIndex == 0 ? 0 : decimals;
  return '${value.toStringAsFixed(fractionDigits)} ${units[unitIndex]}';
}

String? _inferContentType(String? extension) {
  if (extension == null) return null;
  final ext = extension.toLowerCase();
  switch (ext) {
    case 'json':
      return 'application/json';
    case 'png':
      return 'image/png';
    case 'jpg':
    case 'jpeg':
      return 'image/jpeg';
    case 'zip':
      return 'application/zip';
    case 'ttf':
    case 'otf':
      return 'font/ttf';
    default:
      return null;
  }
}
