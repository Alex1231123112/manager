package com.basketbot.service;

import com.basketbot.model.Match;
import com.basketbot.model.Team;
import org.springframework.stereotype.Service;

/**
 * Генерация текста поста после матча (Фаза 2 — шаблоны).
 * Все данные в контексте команды (team передаётся явно).
 */
@Service
public class MatchPostService {

    private static final String TEMPLATE_WIN = "Победа над {opponent} со счётом {our_score}:{opponent_score}!";
    private static final String TEMPLATE_LOSS = "Поражение от {opponent} — {our_score}:{opponent_score}.";
    private static final String TEMPLATE_TIE = "Ничья с {opponent} — {our_score}:{opponent_score}.";

    /**
     * Строит текст поста для завершённого матча (победа/поражение/ничья).
     */
    public String buildPostText(Team team, Match match) {
        if (team == null || match == null
                || match.getOurScore() == null || match.getOpponentScore() == null) {
            return "";
        }
        String template = chooseTemplate(match.getOurScore(), match.getOpponentScore());
        return template
                .replace("{our_team}", team.getName())
                .replace("{opponent}", match.getOpponent())
                .replace("{our_score}", String.valueOf(match.getOurScore()))
                .replace("{opponent_score}", String.valueOf(match.getOpponentScore()));
    }

    private String chooseTemplate(int our, int opp) {
        if (our > opp) return TEMPLATE_WIN;
        if (our < opp) return TEMPLATE_LOSS;
        return TEMPLATE_TIE;
    }
}
