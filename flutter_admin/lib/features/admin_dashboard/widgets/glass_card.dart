import 'dart:ui';
import 'package:flutter/material.dart';

class GlassCard extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry? padding;
  final EdgeInsetsGeometry? margin;
  final double borderRadius;
  final Color? backgroundColor;
  final Color? borderColor;
  final double blur;
  final VoidCallback? onTap;
  final List<BoxShadow>? boxShadow;

  const GlassCard({
    super.key,
    required this.child,
    this.padding,
    this.margin,
    this.borderRadius = 22.0,
    this.backgroundColor,
    this.borderColor,
    this.blur = 0.0,
    this.onTap,
    this.boxShadow,
  });

  @override
  Widget build(BuildContext context) {
    final effectiveBgColor =
        backgroundColor ?? Colors.white.withValues(alpha: 0.78);
    final effectiveBorderColor =
        borderColor ?? Colors.white.withValues(alpha: 0.9);
    final baseAlpha = effectiveBgColor.a.clamp(0.55, 0.88);
    final glassTop = effectiveBgColor.withValues(
      alpha: (baseAlpha + 0.06).clamp(0, 0.92),
    );
    final glassBottom = effectiveBgColor.withValues(
      alpha: (baseAlpha - 0.1).clamp(0.54, 0.78),
    );

    Widget content = Container(
      padding: padding ?? const EdgeInsets.all(16.0),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [glassTop, glassBottom, const Color(0x18EAF0F7)],
          stops: const [0, 0.62, 1],
        ),
        borderRadius: BorderRadius.circular(borderRadius),
        border: Border.all(color: effectiveBorderColor, width: 1),
      ),
      foregroundDecoration: BoxDecoration(
        borderRadius: BorderRadius.circular(borderRadius),
        gradient: const LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [Color(0x24FFFFFF), Color(0x08FFFFFF), Color(0x00000000)],
          stops: [0, 0.42, 1],
        ),
      ),
      child: child,
    );

    Widget cardWidget = Container(
      margin: margin,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(borderRadius),
        border: Border.all(color: const Color(0x22A9B6C8), width: 0.6),
        boxShadow:
            boxShadow ??
            const [
              BoxShadow(
                color: Color(0x151E293B),
                blurRadius: 14,
                offset: Offset(0, 6),
              ),
            ],
      ),
      child: blur > 0
          ? ClipRRect(
              borderRadius: BorderRadius.circular(borderRadius),
              child: BackdropFilter(
                filter: ImageFilter.blur(sigmaX: blur, sigmaY: blur),
                child: content,
              ),
            )
          : content,
    );

    if (onTap != null) {
      return Material(
        color: Colors.transparent,
        borderRadius: BorderRadius.circular(borderRadius),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(borderRadius),
          child: cardWidget,
        ),
      );
    }

    return cardWidget;
  }
}
