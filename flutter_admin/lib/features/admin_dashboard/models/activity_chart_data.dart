class ActivityChartData {
  final String periodKey;
  final String periodName;
  final List<String> labels;
  final List<double> values;
  final String metricTitle;

  const ActivityChartData({
    required this.periodKey,
    required this.periodName,
    required this.labels,
    required this.values,
    required this.metricTitle,
  });
}
