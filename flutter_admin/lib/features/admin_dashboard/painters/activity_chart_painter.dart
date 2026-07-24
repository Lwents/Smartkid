import 'package:flutter/material.dart';

class ActivityChartPainter extends CustomPainter {
  final List<String> labels;
  final List<double> values;

  ActivityChartPainter({required this.labels, required this.values});

  @override
  void paint(Canvas canvas, Size size) {
    if (values.isEmpty) return;

    const double bottomPadding = 30.0;
    const double topPadding = 25.0;
    const double leftPadding = 15.0;
    const double rightPadding = 15.0;

    final double chartWidth = size.width - leftPadding - rightPadding;
    final double chartHeight = size.height - topPadding - bottomPadding;

    final double maxValue = values.reduce((a, b) => a > b ? a : b);
    const double minY = 0.0;
    final double maxY = maxValue == 0 ? 10.0 : maxValue * 1.25;

    // 1. Draw Grid Lines
    final gridPaint = Paint()
      ..color = const Color(0xFFE2E8F0).withValues(alpha: 0.6)
      ..strokeWidth = 1.0
      ..style = PaintingStyle.stroke;

    const int gridRows = 3;
    for (int i = 0; i <= gridRows; i++) {
      final y = topPadding + (chartHeight / gridRows) * i;
      canvas.drawLine(
        Offset(leftPadding, y),
        Offset(size.width - rightPadding, y),
        gridPaint,
      );
    }

    // 2. Compute Points
    final List<Offset> points = [];
    final double stepX = values.length > 1
        ? chartWidth / (values.length - 1)
        : chartWidth / 2;

    for (int i = 0; i < values.length; i++) {
      final x = leftPadding + i * stepX;
      final normalizedY = (values[i] - minY) / (maxY - minY);
      final y = topPadding + chartHeight * (1.0 - normalizedY);
      points.add(Offset(x, y));
    }

    // 3. Build Smooth Bezier Path
    final path = Path();
    final fillPath = Path();

    path.moveTo(points.first.dx, points.first.dy);
    fillPath.moveTo(points.first.dx, topPadding + chartHeight);
    fillPath.lineTo(points.first.dx, points.first.dy);

    for (int i = 0; i < points.length - 1; i++) {
      final current = points[i];
      final next = points[i + 1];

      final control1 = Offset(
        current.dx + (next.dx - current.dx) * 0.4,
        current.dy,
      );
      final control2 = Offset(
        current.dx + (next.dx - current.dx) * 0.6,
        next.dy,
      );

      path.cubicTo(
        control1.dx,
        control1.dy,
        control2.dx,
        control2.dy,
        next.dx,
        next.dy,
      );

      fillPath.cubicTo(
        control1.dx,
        control1.dy,
        control2.dx,
        control2.dy,
        next.dx,
        next.dy,
      );
    }

    fillPath.lineTo(points.last.dx, topPadding + chartHeight);
    fillPath.close();

    // 4. Draw Area Fill
    final areaFillPaint = Paint()
      ..shader = LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: [
          const Color(0xFF635BFF).withValues(alpha: 0.35),
          const Color(0xFF38BDF8).withValues(alpha: 0.08),
          Colors.transparent,
        ],
        stops: const [0.0, 0.7, 1.0],
      ).createShader(Rect.fromLTWH(0, topPadding, size.width, chartHeight));

    canvas.drawPath(fillPath, areaFillPaint);

    // 5. Draw Line Stroke
    final linePaint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3.5
      ..strokeCap = StrokeCap.round
      ..shader = const LinearGradient(
        colors: [Color(0xFF635BFF), Color(0xFF3B82F6), Color(0xFF38BDF8)],
      ).createShader(Rect.fromLTWH(0, 0, size.width, size.height));

    canvas.drawPath(path, linePaint);

    // 6. Draw Points & Tooltip Labels
    final TextPainter textPainter = TextPainter(
      textDirection: TextDirection.ltr,
    );

    for (int i = 0; i < points.length; i++) {
      final pt = points[i];

      // Halo Outer Circle
      final haloPaint = Paint()
        ..color = const Color(0xFF635BFF).withValues(alpha: 0.18)
        ..style = PaintingStyle.fill;
      canvas.drawCircle(pt, 8.0, haloPaint);

      // White Inner Ring
      final whiteDotPaint = Paint()
        ..color = Colors.white
        ..style = PaintingStyle.fill;
      canvas.drawCircle(pt, 5.0, whiteDotPaint);

      // Primary Center Dot
      final centerDotPaint = Paint()
        ..color = const Color(0xFF635BFF)
        ..style = PaintingStyle.fill;
      canvas.drawCircle(pt, 3.0, centerDotPaint);

      // Value label on top
      final valStr = values[i] == values[i].roundToDouble()
          ? values[i].toInt().toString()
          : values[i].toStringAsFixed(1);

      textPainter.text = TextSpan(
        text: valStr,
        style: const TextStyle(
          color: Color(0xFF475569),
          fontSize: 10,
          fontWeight: FontWeight.bold,
        ),
      );
      textPainter.layout();
      textPainter.paint(
        canvas,
        Offset(pt.dx - textPainter.width / 2, pt.dy - 18),
      );

      // X-Axis Label
      if (i < labels.length) {
        textPainter.text = TextSpan(
          text: labels[i],
          style: const TextStyle(
            color: Color(0xFF64748B),
            fontSize: 11,
            fontWeight: FontWeight.w500,
          ),
        );
        textPainter.layout();
        textPainter.paint(
          canvas,
          Offset(pt.dx - textPainter.width / 2, topPadding + chartHeight + 8),
        );
      }
    }
  }

  @override
  bool shouldRepaint(covariant ActivityChartPainter oldDelegate) {
    return oldDelegate.labels != labels || oldDelegate.values != values;
  }
}
