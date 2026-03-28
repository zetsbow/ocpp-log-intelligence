package com.ocpp.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 파일 업로드 크기 초과 처리
     * MaxUploadSizeExceededException → /log 페이지로 redirect + 에러 메시지
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e,
                                               HttpServletRequest request,
                                               RedirectAttributes redirectAttributes) {
        log.warn("[GlobalExceptionHandler] 파일 크기 초과 - maxSize={}, uri={}",
                e.getMaxUploadSize(), request.getRequestURI());
        redirectAttributes.addFlashAttribute("error",
                "파일 크기가 너무 큽니다. 1GB 이하의 파일만 업로드 가능합니다.");
        return "redirect:/log";
    }
}
