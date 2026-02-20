package com.basketbot.model;

import jakarta.persistence.*;

@Entity
@Table(name = "league_table_rows")
public class LeagueTableRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(nullable = false)
    private int position = 1;

    @Column(name = "team_name", nullable = false, length = 200)
    private String teamName;

    @Column(nullable = false)
    private int wins = 0;

    @Column(nullable = false)
    private int losses = 0;

    @Column(name = "points_diff", nullable = false)
    private int pointsDiff = 0;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Team getTeam() { return team; }
    public void setTeam(Team team) { this.team = team; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }
    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }
    public int getPointsDiff() { return pointsDiff; }
    public void setPointsDiff(int pointsDiff) { this.pointsDiff = pointsDiff; }
}
