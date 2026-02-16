package com.basketbot.service;

import com.basketbot.model.Match;
import com.basketbot.model.Team;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Генерация картинки-карточки результата матча (1080×1080 PNG) для соцсетей.
 * Рисуем через Graphics2D без внешних зависимостей.
 */
@Service
public class MatchImageService {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1080;
    private static final String PNG = "PNG";

    /**
     * Генерирует PNG-карточку с результатом матча. Возвращает пустой массив при невалидных данных.
     */
    public byte[] generateScoreCard(Team team, Match match) {
        if (team == null || match == null
                || match.getOurScore() == null || match.getOpponentScore() == null) {
            return new byte[0];
        }

        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Фон — градиент (как в плане)
        GradientPaint gradient = new GradientPaint(0, 0, new Color(102, 126, 234), WIDTH, HEIGHT, new Color(118, 75, 162));
        g.setPaint(gradient);
        g.fill(new RoundRectangle2D.Float(0, 0, WIDTH, HEIGHT, 0, 0));

        g.setColor(Color.WHITE);

        String ourTeam = team.getName();
        String opponent = match.getOpponent();
        String scoreLine = match.getOurScore() + " : " + match.getOpponentScore();

        int padding = 80;
        int y = padding;

        // Заголовок: наша команда
        Font teamFont = new Font(Font.SANS_SERIF, Font.BOLD, 56);
        g.setFont(teamFont);
        drawCenteredString(g, ourTeam, WIDTH / 2, y + 40);
        y += 120;

        // Счёт крупно
        Font scoreFont = new Font(Font.SANS_SERIF, Font.BOLD, 120);
        g.setFont(scoreFont);
        drawCenteredString(g, scoreLine, WIDTH / 2, y + 80);
        y += 180;

        // Соперник
        Font oppFont = new Font(Font.SANS_SERIF, Font.PLAIN, 48);
        g.setFont(oppFont);
        drawCenteredString(g, "против " + opponent, WIDTH / 2, y + 40);

        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, PNG, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Не удалось сгенерировать PNG", e);
        }
    }

    private void drawCenteredString(Graphics2D g, String s, int centerX, int y) {
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(s);
        g.drawString(s, centerX - w / 2, y);
    }
}
