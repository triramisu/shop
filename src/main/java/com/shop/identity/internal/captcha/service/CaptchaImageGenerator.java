package com.shop.identity.internal.captcha.service;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

@Component
class CaptchaImageGenerator {

    private static final int WIDTH = 180;
    private static final int HEIGHT = 64;
    private static final int ANSWER_LENGTH = 6;
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    private final SecureRandom secureRandom = new SecureRandom();

    GeneratedCaptcha generate() {
        String answer = randomAnswer();
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            prepareCanvas(graphics);
            drawNoise(graphics);
            drawAnswer(graphics, answer);
        } finally {
            graphics.dispose();
        }

        return new GeneratedCaptcha(answer, encode(image));
    }

    private String randomAnswer() {
        StringBuilder answer = new StringBuilder(ANSWER_LENGTH);
        for (int index = 0; index < ANSWER_LENGTH; index++) {
            answer.append(ALPHABET[secureRandom.nextInt(ALPHABET.length)]);
        }
        return answer.toString();
    }

    private void prepareCanvas(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(new Color(245, 247, 250));
        graphics.fillRect(0, 0, WIDTH, HEIGHT);
    }

    private void drawNoise(Graphics2D graphics) {
        for (int line = 0; line < 12; line++) {
            graphics.setColor(randomMutedColor());
            graphics.drawLine(
                    secureRandom.nextInt(WIDTH),
                    secureRandom.nextInt(HEIGHT),
                    secureRandom.nextInt(WIDTH),
                    secureRandom.nextInt(HEIGHT));
        }
        for (int dot = 0; dot < 180; dot++) {
            graphics.setColor(randomMutedColor());
            graphics.fillRect(secureRandom.nextInt(WIDTH), secureRandom.nextInt(HEIGHT), 2, 2);
        }
    }

    private void drawAnswer(Graphics2D graphics, String answer) {
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, 34);
        for (int index = 0; index < answer.length(); index++) {
            Graphics2D characterGraphics = (Graphics2D) graphics.create();
            try {
                characterGraphics.setFont(font);
                characterGraphics.setColor(randomDarkColor());
                double rotation = Math.toRadians(secureRandom.nextInt(31) - 15.0);
                int x = 18 + index * 26;
                int y = 43 + secureRandom.nextInt(9) - 4;
                characterGraphics.transform(AffineTransform.getRotateInstance(rotation, x + 10.0, y - 12.0));
                characterGraphics.drawString(String.valueOf(answer.charAt(index)), x, y);
            } finally {
                characterGraphics.dispose();
            }
        }
    }

    private Color randomMutedColor() {
        return new Color(
                120 + secureRandom.nextInt(100), 120 + secureRandom.nextInt(100), 120 + secureRandom.nextInt(100));
    }

    private Color randomDarkColor() {
        return new Color(secureRandom.nextInt(90), secureRandom.nextInt(90), secureRandom.nextInt(90));
    }

    private String encode(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to generate captcha image", exception);
        }
    }

    record GeneratedCaptcha(String answer, String imageData) {}
}
