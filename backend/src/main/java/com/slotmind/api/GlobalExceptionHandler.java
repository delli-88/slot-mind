package com.slotmind.api;

import java.time.Clock;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.slotmind.domain.IllegalTransitionException;
import com.slotmind.service.BadRequestException;
import com.slotmind.service.ConflictException;
import com.slotmind.service.NotFoundException;
import com.slotmind.service.UnprocessableException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private final Clock clock;

	public GlobalExceptionHandler(Clock clock) {
		this.clock = clock;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException ex, HttpServletRequest req) {
		List<String> details = ex.getBindingResult().getFieldErrors().stream()
				.map(f -> f.getField() + ": " + f.getDefaultMessage()).sorted().toList();
		return build(HttpStatus.BAD_REQUEST, "Validation failed", req, details);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
		if (ex.getCause() instanceof InvalidFormatException ife && ife.getTargetType().isEnum()) {
			String field = ife.getPath().isEmpty() ? "body" : ife.getPath().get(ife.getPath().size() - 1).getFieldName();
			String allowed = String.join(", ", java.util.Arrays.stream(ife.getTargetType().getEnumConstants())
					.map(Object::toString).toList());
			return build(HttpStatus.BAD_REQUEST, "Validation failed", req,
					List.of(field + ": must be one of " + allowed));
		}
		return build(HttpStatus.BAD_REQUEST, "Malformed JSON or invalid value", req, List.of());
	}

	@ExceptionHandler(BadRequestException.class)
	ResponseEntity<ApiError> badRequest(BadRequestException ex, HttpServletRequest req) {
		return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req, ex.getDetails());
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ApiError> badParam(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
		Class<?> type = ex.getRequiredType();
		String expected = type != null && type.isEnum()
				? "must be one of " + String.join(", ", java.util.Arrays.stream(type.getEnumConstants())
						.map(Object::toString).toList())
				: "invalid value";
		return build(HttpStatus.BAD_REQUEST, "Invalid request parameter", req,
				List.of(ex.getName() + ": " + expected));
	}

	@ExceptionHandler(NotFoundException.class)
	ResponseEntity<ApiError> notFound(NotFoundException ex, HttpServletRequest req) {
		return build(HttpStatus.NOT_FOUND, ex.getMessage(), req, List.of());
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ApiError> noResource(NoResourceFoundException ex, HttpServletRequest req) {
		return build(HttpStatus.NOT_FOUND, "Resource not found", req, List.of());
	}

	@ExceptionHandler(ConflictException.class)
	ResponseEntity<ApiError> conflict(ConflictException ex, HttpServletRequest req) {
		return build(HttpStatus.CONFLICT, ex.getMessage(), req, List.of(ex.getDetail()));
	}

	@ExceptionHandler(IllegalTransitionException.class)
	ResponseEntity<ApiError> illegalTransition(IllegalTransitionException ex, HttpServletRequest req) {
		return build(HttpStatus.CONFLICT, "Illegal state transition", req,
				List.of("ILLEGAL_TRANSITION: " + ex.getMessage()));
	}

	@ExceptionHandler({ ConcurrencyFailureException.class, DataIntegrityViolationException.class })
	ResponseEntity<ApiError> concurrent(Exception ex, HttpServletRequest req) {
		return build(HttpStatus.CONFLICT, "Resource was modified concurrently, retry", req,
				List.of("CONCURRENT_UPDATE"));
	}

	@ExceptionHandler(UnprocessableException.class)
	ResponseEntity<ApiError> unprocessable(UnprocessableException ex, HttpServletRequest req) {
		return build(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), req, List.of(ex.getCode()));
	}

	/** Anything else: log the cause server side, return nothing internal. */
	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest req) {
		log.error("Unhandled error on {} {}", req.getMethod(), req.getRequestURI(), ex);
		return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", req, List.of());
	}

	private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest req,
			List<String> details) {
		ApiError body = new ApiError(clock.instant(), status.value(), status.getReasonPhrase(), message,
				req.getRequestURI(), details);
		return ResponseEntity.status(status).body(body);
	}

}
