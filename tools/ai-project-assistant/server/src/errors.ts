import type { ErrorRequestHandler, RequestHandler } from "express";
import { ZodError } from "zod";

export class AppError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly status = 400,
    public readonly details: Record<string, unknown> = {},
  ) {
    super(message);
    this.name = "AppError";
  }
}

export const notFoundHandler: RequestHandler = (_request, _response, next) => {
  next(new AppError("ROUTE_NOT_FOUND", "Không tìm thấy API được yêu cầu", 404));
};

export const errorHandler: ErrorRequestHandler = (error, _request, response, _next) => {
  if (error instanceof ZodError) {
    response.status(422).json({
      success: false,
      error: {
        code: "VALIDATION_ERROR",
        message: "Dữ liệu gửi lên không hợp lệ",
        details: { issues: error.issues },
      },
    });
    return;
  }

  const appError =
    error instanceof AppError
      ? error
      : new AppError("INTERNAL_ERROR", "Đã xảy ra lỗi nội bộ", 500, {
          reason: error instanceof Error ? error.message : String(error),
        });

  response.status(appError.status).json({
    success: false,
    error: {
      code: appError.code,
      message: appError.message,
      details: appError.details,
    },
  });
};

export function assert(condition: unknown, code: string, message: string, status = 400): asserts condition {
  if (!condition) {
    throw new AppError(code, message, status);
  }
}
