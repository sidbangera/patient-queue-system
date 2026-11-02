import 'dart:async';
import 'dart:convert';
import 'package:http/http.dart' as http;

class ApiException implements Exception {
  final String message;
  final int? status;
  ApiException(this.message, [this.status]);
  @override
  String toString() => message;
}

/// In-memory bearer token: page reload requires login; no token is persisted to localStorage.
class ApiClient {
  static const configuredBaseUrl = String.fromEnvironment(
      'API_BASE_URL', defaultValue: 'http://localhost:8080');
  final http.Client _client;
  final void Function()? onUnauthorized;
  ApiClient({http.Client? client, this.onUnauthorized}) : _client = client ?? http.Client();
  String? token;
  Map<String, dynamic>? profile;
  String get role => profile?['role']?.toString() ?? '';
  String get username => profile?['username']?.toString() ?? 'Staff';

  Future<dynamic> send(String method, String path,
      {Object? body, Map<String, String>? query}) async {
    final uri = Uri.parse('$configuredBaseUrl$path').replace(queryParameters: query);
    final request = http.Request(method, uri);
    request.headers['Accept'] = path.endsWith('.csv') ? 'text/csv' : 'application/json';
    if (token != null) request.headers['Authorization'] = 'Bearer $token';
    if (body != null) {
      request.headers['Content-Type'] = 'application/json';
      request.body = jsonEncode(body);
    }
    late http.Response response;
    try {
      response = await (() async => http.Response.fromStream(await _client.send(request)))()
          .timeout(const Duration(seconds: 20));
    } on TimeoutException {
      throw ApiException('The request timed out. Refresh to check whether a save succeeded before retrying.');
    } catch (_) {
      throw ApiException('Cannot reach the backend at $configuredBaseUrl. Check the server and network.');
    }
    dynamic parsed;
    try { parsed = response.body.isEmpty ? null : jsonDecode(response.body); }
    catch (_) { parsed = response.body; }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      var message = 'Request failed (${response.statusCode})';
      if (parsed is Map) {
        message = parsed['error']?.toString() ?? parsed['message']?.toString() ?? message;
      } else if (parsed is String && parsed.isNotEmpty) { message = parsed; }
      if (response.statusCode == 401) {
        final hadSession = token != null;
        logout();
        if (hadSession) onUnauthorized?.call();
      }
      throw ApiException(message, response.statusCode);
    }
    return parsed;
  }
  Future<void> login(String username, String password) async {
    final response = await send('POST', '/api/users/login', body: {'username': username, 'password': password});
    if (response is! Map || response['token'] is! String || (response['token'] as String).isEmpty) {
      throw ApiException('The server did not return a login token.');
    }
    token = response['token'] as String;
    try { profile = Map<String, dynamic>.from(await send('GET', '/api/users/me') as Map); }
    catch (_) { logout(); rethrow; }
  }
  void logout() { token = null; profile = null; }
  void close() => _client.close();
  Future<List<Map<String, dynamic>>> list(String path, {Map<String, String>? query}) async {
    final response = await send('GET', path, query: query);
    if (response is! List) throw ApiException('Unexpected response from $path');
    return response.map((e) => Map<String, dynamic>.from(e as Map)).toList();
  }
}
