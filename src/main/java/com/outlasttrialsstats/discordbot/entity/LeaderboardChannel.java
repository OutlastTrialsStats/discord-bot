package com.outlasttrialsstats.discordbot.entity;

import com.outlasttrialsstats.backend.api.model.StatisticType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "leaderboard_channel", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"guild_id", "category"})
})
@Getter
@Setter
@NoArgsConstructor
public class LeaderboardChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "guild_id", nullable = false)
    private String guildId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatisticType category;

    @Column(name = "channel_id", nullable = false)
    private String channelId;

    @ElementCollection
    @CollectionTable(name = "leaderboard_channel_messages", joinColumns = @JoinColumn(name = "leaderboard_channel_id"))
    @Column(name = "message_id")
    @OrderColumn(name = "page_index")
    private List<String> messageIds = new ArrayList<>();

    @Column(name = "max_pages", nullable = false)
    private int maxPages = 1;

    public LeaderboardChannel(String guildId, StatisticType category, String channelId, List<String> messageIds, int maxPages) {
        this.guildId = guildId;
        this.category = category;
        this.channelId = channelId;
        this.messageIds = new ArrayList<>(messageIds);
        this.maxPages = maxPages;
    }
}
