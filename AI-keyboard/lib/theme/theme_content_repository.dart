import 'dart:io';
import 'dart:typed_data';

import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:firebase_storage/firebase_storage.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import 'package:path_provider/path_provider.dart';

import '../theme/theme_v2.dart';

class ThemeBackgroundAsset {
  ThemeBackgroundAsset({
    required this.id,
    required this.name,
    required this.category,
    required this.background,
    this.previewImage,
  });

  final String id;
  final String name;
  final String category;
  final ThemeBackground background;
  final String? previewImage;

  ThemeBackground withOpacity(double opacity) {
    return background.copyWith(
      imageOpacity: opacity.clamp(0.0, 1.0),
    );
  }
}

class KeyboardFontAsset {
  KeyboardFontAsset({
    required this.id,
    required this.name,
    required this.family,
    required this.storagePath,
    this.previewText,
    this.defaultScale = 1.0,
  });

  final String id;
  final String name;
  final String family;
  final String storagePath;
  final String? previewText;
  final double defaultScale;
}

/// Fetches theme resources (backgrounds, fonts) from Firebase and caches them locally.
class ThemeContentRepository {
  ThemeContentRepository._();

  static final ThemeContentRepository instance = ThemeContentRepository._();

  final FirebaseFirestore _firestore = FirebaseFirestore.instance;
  final FirebaseStorage _storage = FirebaseStorage.instance;

  final Map<String, Future<void>> _fontLoadQueue = {};
  final Set<String> _loadedFontFamilies = <String>{};

  Future<List<ThemeBackgroundAsset>> loadBackgrounds({bool includeGradients = true}) async {
    final List<ThemeBackgroundAsset> assets = [];

    try {
      final snapshot = await _firestore.collection('themes_backgrounds').get();
      for (final doc in snapshot.docs) {
        final data = doc.data();
        final type = (data['type'] as String?)?.toLowerCase() ?? 'image';
        final category = data['category'] as String? ?? 'General';
        final name = data['name'] as String? ?? doc.id;

        if (type == 'gradient') {
          final colors = <Color>[];
          final rawColors = data['colors'];
          if (rawColors is Iterable) {
            for (final value in rawColors) {
              final color = _parseColor(value?.toString());
              if (color != null) {
                colors.add(color);
              }
            }
          }
          if (colors.length >= 2) {
            assets.add(
              ThemeBackgroundAsset(
                id: doc.id,
                name: name,
                category: category,
                background: ThemeBackground(
                  type: 'gradient',
                  color: null,
                  imagePath: null,
                  imageOpacity: 1.0,
                  gradient: ThemeGradient(
                    colors: colors,
                    orientation: (data['orientation'] as String?) ?? 'TOP_BOTTOM',
                    stops: (data['stops'] as List<dynamic>?)
                        ?.map((e) => (e as num).toDouble())
                        .toList(),
                  ),
                  overlayEffects: const [],
                  adaptive: null,
                  brightness: (data['brightness'] as num?)?.toDouble() ?? 1.0,
                ),
              ),
            );
          }
          continue;
        }

        final path = data['storagePath'] as String? ?? data['imagePath'] as String?;
        if (path == null || path.isEmpty) {
          continue;
        }

        final resolvedUrl = await _resolveStoragePath(path);

        assets.add(
          ThemeBackgroundAsset(
            id: doc.id,
            name: name,
            category: category,
            background: ThemeBackground(
              type: 'image',
              color: Colors.transparent,
              imagePath: resolvedUrl,
              imageOpacity: (data['opacity'] as num?)?.toDouble() ?? 0.85,
              gradient: null,
              overlayEffects: const [],
              adaptive: null,
              brightness: (data['brightness'] as num?)?.toDouble() ?? 1.0,
            ),
            previewImage: resolvedUrl,
          ),
        );
      }
    } catch (_) {
      // Ignore remote load failures; fall back to gradients.
    }

    if (includeGradients) {
      assets.addAll(_localGradients());
    }

    // Stable ordering: gradients after remote assets
    return assets;
  }

  Future<List<KeyboardFontAsset>> loadFonts() async {
    final List<KeyboardFontAsset> fonts = [];
    try {
      final snapshot = await _firestore.collection('fonts').get();
      for (final doc in snapshot.docs) {
        final data = doc.data();
        final storagePath = data['storagePath'] as String? ?? data['path'] as String?;
        if (storagePath == null || storagePath.isEmpty) {
          continue;
        }
        fonts.add(
          KeyboardFontAsset(
            id: doc.id,
            name: data['name'] as String? ?? doc.id,
            family: data['family'] as String? ?? doc.id,
            storagePath: storagePath,
            previewText: data['preview'] as String?,
            defaultScale: (data['scale'] as num?)?.toDouble() ?? 1.0,
          ),
        );
      }
    } catch (_) {
      // Return whatever fonts we managed to collect.
    }
    return fonts;
  }

  Future<void> ensureFontLoaded(KeyboardFontAsset font) {
    if (_loadedFontFamilies.contains(font.family)) {
      return Future<void>.value();
    }
    return _fontLoadQueue.putIfAbsent(font.family, () async {
      try {
        final file = await _downloadFont(font);
        final bytes = await file.readAsBytes();
        final loader = FontLoader(font.family);
        loader.addFont(Future<ByteData>.value(
          ByteData.view(Uint8List.fromList(bytes).buffer),
        ));
        await loader.load();
        _loadedFontFamilies.add(font.family);
      } catch (_) {
        // Ignore failures; font fallback will be used.
      } finally {
        _fontLoadQueue.remove(font.family);
      }
    });
  }

  Future<File> _downloadFont(KeyboardFontAsset font) async {
    final directory = await _fontsDirectory();
    final sanitized = font.storagePath.replaceAll(RegExp(r'[^\w.\-]'), '_');
    final file = File('${directory.path}/$sanitized');
    if (await file.exists()) {
      return file;
    }

    final uri = await _resolveStoragePath(font.storagePath);
    if (uri == null) {
      return file;
    }

    if (uri.startsWith('http')) {
      final response = await http.get(Uri.parse(uri));
      if (response.statusCode == 200) {
        await file.writeAsBytes(response.bodyBytes, flush: true);
      }
      return file;
    }

    try {
      final ref = _storage.refFromURL(uri);
      await ref.writeToFile(file);
    } catch (_) {
      try {
        final ref = _storage.ref(uri);
        await ref.writeToFile(file);
      } catch (_) {
        // Give up; caller will fall back to default font.
      }
    }
    return file;
  }

  Future<Directory> _fontsDirectory() async {
    final baseDir = await getApplicationSupportDirectory();
    final dir = Directory('${baseDir.path}/remote_fonts');
    if (!await dir.exists()) {
      await dir.create(recursive: true);
    }
    return dir;
  }

  Future<String?> _resolveStoragePath(String rawPath) async {
    final trimmed = rawPath.trim();
    if (trimmed.isEmpty) {
      return null;
    }
    if (trimmed.startsWith('http')) {
      return trimmed;
    }
    if (trimmed.startsWith('gs://')) {
      try {
        final ref = _storage.refFromURL(trimmed);
        return await ref.getDownloadURL();
      } catch (_) {
        return null;
      }
    }
    try {
      final ref = _storage.ref(trimmed);
      return await ref.getDownloadURL();
    } catch (_) {
      return null;
    }
  }

  List<ThemeBackgroundAsset> _localGradients() {
    const gradients = [
      (id: 'gradient_sunrise', name: 'Sunrise', colors: [Color(0xFFFF9A9E), Color(0xFFFAD0C4)]),
      (id: 'gradient_mint', name: 'Mint', colors: [Color(0xFF96E6B3), Color(0xFFD4FC79)]),
      (id: 'gradient_lavender', name: 'Lavender', colors: [Color(0xFFB7A0FF), Color(0xFFFED6FF)]),
      (id: 'gradient_ocean', name: 'Ocean', colors: [Color(0xFF00C6FB), Color(0xFF005BEA)]),
      (id: 'gradient_midnight', name: 'Midnight', colors: [Color(0xFF141E30), Color(0xFF243B55)]),
    ];

    return gradients
        .map(
          (entry) => ThemeBackgroundAsset(
            id: entry.id,
            name: entry.name,
            category: 'Gradients',
            background: ThemeBackground(
              type: 'gradient',
              color: null,
              imagePath: null,
              imageOpacity: 1.0,
              gradient: ThemeGradient(
                colors: entry.colors,
                orientation: 'TOP_BOTTOM',
              ),
              overlayEffects: const [],
              adaptive: null,
              brightness: 1.0,
            ),
            previewImage: null,
          ),
        )
        .toList();
  }
}

Color? _parseColor(String? value) {
  if (value == null || value.isEmpty) {
    return null;
  }
  final hex = value.replaceFirst('#', '');
  if (hex.length != 6 && hex.length != 8) {
    return null;
  }
  final normalized = hex.length == 6 ? 'FF$hex' : hex;
  return Color(int.parse(normalized, radix: 16));
}
