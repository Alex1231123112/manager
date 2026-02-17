package com.basketbot.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

@Service
public class QrCodeService {

    /**
     * Генерирует QR-код с заданным содержимым (обычно URL) в виде PNG.
     *
     * @param content текст или URL для кодирования
     * @param size    размер стороны в пикселях (например 256)
     * @return PNG в виде byte[]
     */
    public byte[] generatePng(String content, int size) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Content must not be empty");
        }
        try {
            BitMatrix bitMatrix = new MultiFormatWriter().encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    size,
                    size
            );
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }
}
