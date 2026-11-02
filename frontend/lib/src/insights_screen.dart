import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'api.dart';
import 'ui.dart';

/// Administrative cohort analytics. No patient-level records are requested.
/// A cohort is defined by check-in service_date, not completion date.
class InsightsScreen extends StatefulWidget {
  final ApiClient api;
  const InsightsScreen({super.key, required this.api});

  @override
  State<InsightsScreen> createState() => _InsightsScreenState();
}

class _InsightsScreenState extends State<InsightsScreen> {
  late DateTimeRange _range;
  DateTime? _clinicToday;
  String _asOf = "";
  String _clinicZone = "Asia/Kolkata";
  int? _doctorId;
  int _requestId = 0;
  bool _loading = true;
  bool _copyingCsv = false;
  String? _error;
  Map<String, dynamic>? _summary;
  Map<String, dynamic>? _comparison;
  Map<String, dynamic>? _quality;
  List<Map<String, dynamic>> _doctorOptions = [];
  List<Map<String, dynamic>> _daily = [];
  List<Map<String, dynamic>> _workload = [];
  List<Map<String, dynamic>> _hourly = [];

  @override
  void initState() {
    super.initState();
    final now = _clinicToday ?? DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    _range = DateTimeRange(
      start: DateTime(today.year, today.month, today.day - 6),
      end: today,
    );
    _initialize();
  }

  String _date(DateTime date) => '${date.year.toString().padLeft(4, '0')}-'
      '${date.month.toString().padLeft(2, '0')}-'
      '${date.day.toString().padLeft(2, '0')}';

  Map<String, String> get _query => <String, String>{
        'from': _date(_range.start),
        'to': _date(_range.end),
        if (_doctorId != null) 'doctorId': '$_doctorId',
      };

  Future<void> _initialize() async {
    final currentRequest = ++_requestId;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final doctors = await widget.api.list('/api/doctors');
      final report = Map<String, dynamic>.from(
          await widget.api.send('GET', '/api/analytics/dashboard') as Map);
      if (!mounted || currentRequest != _requestId) return;
      setState(() {
        _doctorOptions = doctors;
        _range = DateTimeRange(
            start: DateTime.parse(report['from'] as String),
            end: DateTime.parse(report['to'] as String));
        _applyReport(report);
        _loading = false;
      });
    } catch (error) {
      if (!mounted || currentRequest != _requestId) return;
      setState(() {
        _error = error.toString();
        _loading = false;
      });
    }
  }

  void _applyReport(Map<String, dynamic> report) {
    _summary = Map<String, dynamic>.from(report['summary'] as Map);
    _daily = List<Map<String, dynamic>>.from(report['daily'] as List);
    _workload = List<Map<String, dynamic>>.from(report['doctors'] as List);
    _hourly = List<Map<String, dynamic>>.from(report['hourly'] as List);
    _comparison = Map<String, dynamic>.from(report['comparison'] as Map);
    _quality = Map<String, dynamic>.from(report['quality'] as Map);
    _clinicToday = DateTime.parse(report['clinicToday'] as String);
    _clinicZone = report['clinicZone'].toString();
    _asOf = report['asOf'].toString();
  }

  /// A single backend snapshot keeps cards internally consistent. Ignore stale responses.

  Future<void> _reload() async {
    final requestId = ++_requestId;
    final query = _query;

    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final report = Map<String, dynamic>.from(
        await widget.api.send(
          'GET',
          '/api/analytics/dashboard',
          query: query,
        ) as Map,
      );

      if (!mounted || requestId != _requestId) {
        return;
      }

      setState(() {
        _applyReport(report);
      });
    } catch (error) {
      if (!mounted || requestId != _requestId) {
        return;
      }

      setState(() {
        _error = error.toString();
      });
    } finally {
      if (mounted && requestId == _requestId) {
        setState(() {
          _loading = false;
        });
      }
    }
  }

  void _selectPreset(int days) {
    final now = _clinicToday ?? DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    setState(() {
      _range = DateTimeRange(
        start: DateTime(today.year, today.month, today.day - days + 1),
        end: today,
      );
    });
    _reload();
  }

  bool _isPreset(int days) {
    final now = _clinicToday ?? DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final start = DateTime(today.year, today.month, today.day - days + 1);
    return _date(_range.start) == _date(start) &&
        _date(_range.end) == _date(today);
  }

  Future<void> _pickDates() async {
    final result = await showDateRangePicker(
      context: context,
      initialDateRange: _range,
      firstDate: DateTime(2020),
      lastDate: DateTime(DateTime.now().year + 1, 12, 31),
      helpText: 'Choose service dates (maximum 366 days)',
    );
    if (result == null || !mounted) return;
    if (DateTime.utc(result.end.year, result.end.month, result.end.day)
            .difference(DateTime.utc(
                result.start.year, result.start.month, result.start.day))
            .inDays >
        365) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Choose at most 366 inclusive days.')),
      );
      return;
    }
    setState(() => _range = result);
    _reload();
  }

  Future<void> _copyCsv() async {
    if (_copyingCsv) return;
    setState(() => _copyingCsv = true);
    try {
      final result = await widget.api.send(
        'GET',
        '/api/analytics/export.csv',
        query: _query,
      );
      if (result is! String) throw ApiException('Expected CSV text from API');
      await Clipboard.setData(ClipboardData(text: result));
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text(
            'Aggregate CSV copied. Paste into a text file and save it as .csv.'),
      ));
    } catch (error) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('CSV export failed: $error')),
        );
      }
    } finally {
      if (mounted) setState(() => _copyingCsv = false);
    }
  }

  String _formatted(dynamic number, {String suffix = ''}) {
    if (number is! num) return '—';
    return '${number.toStringAsFixed(number is int ? 0 : 1)}$suffix';
  }

  int _count(Map<String, dynamic> row, String key) =>
      (row[key] is num) ? (row[key] as num).toInt() : 0;

  Widget _filterBar() => Wrap(
        spacing: 10,
        runSpacing: 10,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          for (final days in [7, 30, 90])
            ChoiceChip(
              label: Text('$days days'),
              selected: _isPreset(days),
              onSelected: _loading ? null : (_) => _selectPreset(days),
            ),
          OutlinedButton.icon(
            onPressed: _loading ? null : _pickDates,
            icon: const Icon(Icons.date_range),
            label: Text('${_date(_range.start)} to ${_date(_range.end)}'),
          ),
          SizedBox(
            width: 255,
            child: DropdownButtonFormField<int>(
              key: ValueKey('doctor-${_doctorId ?? 0}'),
              initialValue: _doctorId ?? 0,
              isExpanded: true,
              decoration: const InputDecoration(
                labelText: 'Doctor',
                prefixIcon: Icon(Icons.medical_services_outlined),
              ),
              items: [
                const DropdownMenuItem(value: 0, child: Text('All doctors')),
                for (final doctor in _doctorOptions)
                  DropdownMenuItem<int>(
                    value: doctor['id'] as int,
                    child: Text(
                      '${doctor['name']} (ID ${doctor['id']})',
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
              ],
              onChanged: _loading
                  ? null
                  : (id) {
                      setState(() => _doctorId = id == 0 ? null : id);
                      _reload();
                    },
            ),
          ),
          OutlinedButton.icon(
            onPressed: _loading ? null : _reload,
            icon: const Icon(Icons.refresh),
            label: const Text('Refresh'),
          ),
          FilledButton.tonalIcon(
            onPressed: _copyingCsv || _loading ? null : _copyCsv,
            icon: const Icon(Icons.copy_all_outlined),
            label: Text(_copyingCsv ? 'Copying...' : 'Copy CSV'),
          ),
        ],
      );

  Widget _metrics() {
    final data = _summary!;
    final total = _count(data, 'total');
    final sampleSize = _count(data, 'waitSampleSize');
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Wrap(spacing: 12, runSpacing: 12, children: [
        _metric('Check-ins', '$total', Icons.people_outline),
        _metric('Completed', '${_count(data, 'completed')}',
            Icons.check_circle_outline),
        _metric(
            'Completion rate',
            total == 0
                ? '—'
                : _formatted(data['completionRatePct'], suffix: '%'),
            Icons.donut_large),
        _metric(
            'No-show rate',
            total == 0 ? '—' : _formatted(data['noShowRatePct'], suffix: '%'),
            Icons.person_off_outlined),
        _metric('Average wait',
            _formatted(data['avgWaitMinutes'], suffix: ' min'), Icons.schedule),
        _metric('P90 wait', _formatted(data['p90WaitMinutes'], suffix: ' min'),
            Icons.av_timer),
        _metric(
            'Average consultation',
            _formatted(data['avgConsultationMinutes'], suffix: ' min'),
            Icons.timer_outlined),
      ]),
      const SizedBox(height: 14),
      Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Wrap(spacing: 18, runSpacing: 8, children: [
            _statusLabel(
                'Waiting', _count(data, 'waiting'), Icons.hourglass_empty),
            _statusLabel('In consultation', _count(data, 'inConsultation'),
                Icons.medical_services_outlined),
            _statusLabel('Completed', _count(data, 'completed'),
                Icons.check_circle_outline),
            _statusLabel(
                'No-show', _count(data, 'noShows'), Icons.person_off_outlined),
          ]),
        ),
      ),
      const SizedBox(height: 10),
      Text(
        'Waiting-time metrics use $sampleSize visit${sampleSize == 1 ? '' : 's'} '
        'with a valid consultation start timestamp. '
        'Percentages use check-ins during the selected service dates as denominator. '
        'Waiting counts describe that cohort, not necessarily the current live queue.',
        style: const TextStyle(color: muted, fontSize: 12),
      ),
      if (total > 0 && sampleSize < 5)
        const Padding(
          padding: EdgeInsets.only(top: 7),
          child: Text(
            'Small sample: waiting-time percentiles are illustrative, not reliable staffing forecasts.',
            style: TextStyle(color: muted, fontSize: 12),
          ),
        ),
    ]);
  }

  Widget _metric(String label, String value, IconData icon) => SizedBox(
      width: 205, child: infoCard(label: label, value: value, icon: icon));

  Widget _statusLabel(String title, int n, IconData icon) =>
      Row(mainAxisSize: MainAxisSize.min, children: [
        Icon(icon, size: 18, color: teal),
        const SizedBox(width: 6),
        Text('$title: $n', style: const TextStyle(color: ink)),
      ]);

  Widget _dailyPanel() {
    final recent =
        _daily.length > 31 ? _daily.sublist(_daily.length - 31) : _daily;
    final peak = recent.fold<int>(
        1,
        (value, row) =>
            _count(row, 'checkins') > value ? _count(row, 'checkins') : value);
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      sectionHeading('Daily arrivals vs completed visits',
          caption:
              'Check-in cohort by service date; zero-activity days are shown'),
      const SizedBox(height: 12),
      Card(
          child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(children: [
          Row(children: [
            _legend(teal, 'Check-ins'),
            const SizedBox(width: 18),
            _legend(const Color(0xFF7DB8B2), 'Completed'),
          ]),
          const SizedBox(height: 14),
          if (recent.every((row) => _count(row, 'checkins') == 0))
            emptyNotice('No check-ins in the selected period.'),
          for (final row in recent)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 5),
              child: Row(children: [
                SizedBox(
                  width: 88,
                  child: Text(row['date'].toString().substring(5),
                      style: const TextStyle(color: muted, fontSize: 12)),
                ),
                Expanded(
                    child: Column(children: [
                  _progressBar(_count(row, 'checkins') / peak, teal),
                  const SizedBox(height: 3),
                  _progressBar(
                      _count(row, 'completed') / peak, const Color(0xFF7DB8B2)),
                ])),
                SizedBox(
                  width: 64,
                  child: Text(
                    '${_count(row, 'checkins')} / ${_count(row, 'completed')}',
                    textAlign: TextAlign.right,
                    style: const TextStyle(fontSize: 12, color: muted),
                  ),
                ),
              ]),
            ),
          if (_daily.length > 31)
            const Padding(
              padding: EdgeInsets.only(top: 12),
              child: Text(
                  'Showing the latest 31 days; summary KPIs and CSV cover the entire selected range.',
                  style: TextStyle(color: muted, fontSize: 12)),
            ),
        ]),
      )),
    ]);
  }

  Widget _legend(Color color, String label) => Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(width: 11, height: 11, color: color),
          const SizedBox(width: 6),
          Text(label, style: const TextStyle(fontSize: 12, color: muted)),
        ],
      );

  Widget _progressBar(double fraction, Color color) => ClipRRect(
        borderRadius: BorderRadius.circular(5),
        child: LinearProgressIndicator(
          value: fraction.clamp(0.0, 1.0).toDouble(),
          minHeight: 8,
          backgroundColor: mist,
          color: color,
        ),
      );

  Widget _doctorPanel() {
    // Only display doctors with activity in the selected reporting period.
    final activeDoctors =
        _workload.where((doctor) => _count(doctor, 'checkins') > 0).toList();

    final peak = activeDoctors.fold<int>(
      1,
      (value, row) =>
          _count(row, 'checkins') > value ? _count(row, 'checkins') : value,
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        sectionHeading(
          'Doctor workload',
          caption: 'Compare check-in volume and completed visits by doctor',
        ),
        const SizedBox(height: 12),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(18),
            child: Column(
              children: [
                if (activeDoctors.isEmpty)
                  emptyNotice('No doctor check-ins for the selected period.'),
                for (final doctor in activeDoctors)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 10),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Wrap(
                          alignment: WrapAlignment.spaceBetween,
                          spacing: 12,
                          runSpacing: 4,
                          children: [
                            Text(
                              '${doctor['doctorName']} (ID ${doctor['doctorId']})',
                              style: const TextStyle(
                                fontWeight: FontWeight.w700,
                                color: ink,
                              ),
                            ),
                            Text(
                              '${_count(doctor, 'checkins')} check-ins · '
                              '${_count(doctor, 'completed')} completed · '
                              '${_formatted(doctor['avgWaitMinutes'], suffix: ' min')} avg wait',
                              style: const TextStyle(
                                color: muted,
                                fontSize: 12,
                              ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 7),
                        _progressBar(
                          _count(doctor, 'checkins') / peak,
                          teal,
                        ),
                      ],
                    ),
                  ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _hourlyPanel() {
    final peak = _hourly.fold<int>(
        1,
        (value, row) =>
            _count(row, 'checkins') > value ? _count(row, 'checkins') : value);
    final hasVisits = _hourly.any((row) => _count(row, 'checkins') > 0);
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      sectionHeading('Arrival time distribution',
          caption:
              'Check-ins by local clinic hour, summed over selected dates'),
      const SizedBox(height: 12),
      Card(
          child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          if (!hasVisits) emptyNotice('No arrival-hour data for this period.'),
          if (hasVisits)
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  for (final row in _hourly)
                    Tooltip(
                      message: '${row['hour'].toString().padLeft(2, '0')}:00 — '
                          '${_count(row, 'checkins')} check-in(s)',
                      child: Semantics(
                        label:
                            'Hour ${row['hour']}: ${_count(row, 'checkins')} check-ins',
                        child: Padding(
                          padding: const EdgeInsets.only(right: 8),
                          child: Column(children: [
                            SizedBox(
                              width: 24,
                              height: 125,
                              child: Align(
                                alignment: Alignment.bottomCenter,
                                child: FractionallySizedBox(
                                  heightFactor: _count(row, 'checkins') / peak,
                                  widthFactor: 0.8,
                                  child: DecoratedBox(
                                    decoration: BoxDecoration(
                                      color: teal,
                                      borderRadius: BorderRadius.circular(4),
                                    ),
                                  ),
                                ),
                              ),
                            ),
                            const SizedBox(height: 6),
                            Text('${row['hour']}',
                                style: const TextStyle(
                                    color: muted, fontSize: 11)),
                          ]),
                        ),
                      ),
                    ),
                ],
              ),
            ),
        ]),
      )),
    ]);
  }

  Widget _comparisonPanel() {
    final comparison = _comparison;
    if (comparison == null) return const SizedBox.shrink();
    final current = Map<String, dynamic>.from(comparison['current'] as Map);
    final previous = Map<String, dynamic>.from(comparison['previous'] as Map);
    final currentTotal = _count(current, 'total');
    final previousTotal = _count(previous, 'total');
    final delta = _count(comparison, 'checkinsDelta');
    final deltaCompleted = _count(comparison, 'completedDelta');
    final change = comparison['checkinsChangePct'];
    final rateDelta = comparison['completionRateDeltaPoints'];
    final period =
        '${comparison['previousFrom']} to ${comparison['previousTo']}';
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      sectionHeading('Previous-period comparison',
          caption: 'Same length and doctor filter; previous period: $period'),
      const SizedBox(height: 12),
      Card(
          child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Wrap(spacing: 12, runSpacing: 12, children: [
            _metric('Current check-ins', '$currentTotal', Icons.today_outlined),
            _metric('Previous check-ins', '$previousTotal', Icons.history),
            _metric('Check-in difference', '${delta >= 0 ? '+' : ''}$delta',
                Icons.compare_arrows),
            _metric(
                'Completed difference',
                '${deltaCompleted >= 0 ? '+' : ''}$deltaCompleted',
                Icons.fact_check_outlined),
            _metric(
                'Check-in change',
                change is num ? _formatted(change, suffix: '%') : 'N/A',
                Icons.trending_up_outlined),
            _metric(
                'Completion rate difference',
                rateDelta is num ? _formatted(rateDelta, suffix: ' pp') : 'N/A',
                Icons.donut_large),
          ]),
          const SizedBox(height: 12),
          const Text(
              'Positive or negative differences are descriptive, not automatically improvements. '
              'Percentage change is N/A when the preceding period has zero check-ins. '
              'pp means percentage points.',
              style: TextStyle(color: muted, fontSize: 12)),
        ]),
      )),
    ]);
  }

  Widget _qualityPanel() {
    final quality = _quality;
    if (quality == null) return const SizedBox.shrink();
    final checked = _count(quality, 'recordsChecked');
    final flagged = _count(quality, 'flaggedVisits');
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      sectionHeading('Data quality checks',
          caption:
              'Read-only aggregate checks for the selected service-date cohort'),
      const SizedBox(height: 12),
      Card(
          child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Wrap(spacing: 12, runSpacing: 12, children: [
            _metric('Records checked', '$checked', Icons.fact_check_outlined),
            _metric(
                'Flagged visits', '$flagged', Icons.report_problem_outlined),
          ]),
          const SizedBox(height: 12),
          _qualityRow(
              'Missing patient / doctor reference', 'missingReferences'),
          _qualityRow('Missing check-in timestamp', 'missingCheckIn'),
          _qualityRow('State / timestamp mismatch', 'stateTimestampMismatch'),
          _qualityRow('Service-date mismatch', 'serviceDateMismatch'),
          _qualityRow('Consultation before check-in', 'negativeWait'),
          _qualityRow('Consultation ends before start', 'negativeConsultation'),
          _qualityRow('Completed visits missing timestamps',
              'completedMissingTimestamps'),
          const SizedBox(height: 8),
          const Text(
              'A visit may fail more than one check. Flagged visits are counted once. '
              'No patient identifiers or individual visit records are exposed here.',
              style: TextStyle(color: muted, fontSize: 12)),
        ]),
      )),
    ]);
  }

  Widget _qualityRow(String label, String field) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(children: [
          Expanded(child: Text(label, style: const TextStyle(color: ink))),
          Text('${_count(_quality!, field)}',
              style: const TextStyle(fontWeight: FontWeight.w700, color: ink)),
        ]),
      );

  @override
  Widget build(BuildContext context) => RefreshIndicator(
        onRefresh: _reload,
        child: ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(20),
            children: [
              sectionHeading('Operations overview',
                  caption:
                      'Administrator-only · aggregated operational KPIs · synthetic data'),
              const SizedBox(height: 18),
              _filterBar(),
              const SizedBox(height: 16),
              if (_loading)
                const Center(
                    child: Padding(
                  padding: EdgeInsets.all(40),
                  child: CircularProgressIndicator(),
                )),
              if (_error != null)
                Card(
                    child: Padding(
                  padding: const EdgeInsets.all(18),
                  child: Column(children: [
                    Text('Unable to load analytics: $_error',
                        style: const TextStyle(color: Colors.red)),
                    TextButton(
                        onPressed: _summary == null ? _initialize : _reload,
                        child: const Text('Retry')),
                  ]),
                )),
              if (!_loading && _error == null && _summary != null) ...[
                Text('Clinic timezone: $_clinicZone | Snapshot: $_asOf',
                    style: const TextStyle(color: muted, fontSize: 11)),
                const SizedBox(height: 12),
                _metrics(),
                const SizedBox(height: 28),
                _comparisonPanel(),
                const SizedBox(height: 28),
                _qualityPanel(),
                const SizedBox(height: 28),
                _dailyPanel(),
                const SizedBox(height: 28),
                _doctorPanel(),
                const SizedBox(height: 28),
                _hourlyPanel(),
                const SizedBox(height: 18),
                const Text(
                  'Definitions: completion rate = completed / check-ins; '
                  'no-show rate = no-shows / check-ins; wait = consultation start − check-in. '
                  'Only records with valid timestamps contribute to wait metrics. '
                  'Exports contain doctor/day aggregates, not patient identifiers.',
                  style: TextStyle(color: muted, fontSize: 12),
                ),
              ],
              const SizedBox(height: 65),
            ]),
      );
}
