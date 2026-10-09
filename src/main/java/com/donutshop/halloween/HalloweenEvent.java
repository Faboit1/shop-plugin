package com.donutshop.halloween;

import com.donutshop.economy.EconomyManager;
import com.donutshop.util.NumberFormatter;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Halloween event: players earn a pumpkin currency for time played, hear about it
 * through a one-time intro dialog, and spend it in the shop's Halloween category.
 *
 * Progress towards the next pumpkin lives in the player's PDC so it survives relogs.
 * Every per-player task runs on that player's entity scheduler (Folia-safe).
 */
public class HalloweenEvent implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final JavaPlugin plugin;
    private final NamespacedKey progressKey;
    private final NamespacedKey dialogSeenKey;
    private final Map<UUID, ScheduledTask> timers = new ConcurrentHashMap<>();

    private volatile Settings settings;
    private volatile EconomyManager economy;
    private volatile Dialog dialog;

    public HalloweenEvent(JavaPlugin plugin) {
        this.plugin = plugin;
        this.progressKey = new NamespacedKey(plugin, "halloween_progress");
        this.dialogSeenKey = new NamespacedKey(plugin, "halloween_dialog_seen");
        loadSettings();
    }

    // ── Lifecycle ─────────────────────────────────────────────

    /** Must run after other plugins are enabled so the economy can be hooked. */
    public void start() {
        hookEconomy();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.getScheduler().run(plugin, t -> startTimer(player), null);
        }
    }

    public void reload() {
        stopAll();
        loadSettings();
        start();
    }

    public void stopAll() {
        timers.values().forEach(ScheduledTask::cancel);
        timers.clear();
    }

    public boolean isEnabled() {
        return settings.enabled;
    }

    private void loadSettings() {
        File file = new File(plugin.getDataFolder(), "halloween.yml");
        if (!file.exists()) {
            plugin.saveResource("halloween.yml", false);
        }
        settings = Settings.load(YamlConfiguration.loadConfiguration(file));
        dialog = null;
        if (settings.enabled && settings.dialogEnabled) {
            try {
                dialog = buildDialog(settings);
            } catch (Throwable t) {
                plugin.getLogger().severe("[Halloween] Could not build the intro dialog: " + t);
            }
        }
    }

    private void hookEconomy() {
        Settings s = settings;
        if (!s.enabled) {
            economy = null;
            return;
        }
        EconomyManager econ = new EconomyManager(plugin, s.currencyProvider, s.currencyId, s.currencyId);
        if (econ.isReady() && econ.getProviderName().equalsIgnoreCase(s.currencyProvider)) {
            economy = econ;
            plugin.getLogger().info("[Halloween] Paying pumpkins with " + econ.getProviderName()
                    + " currency '" + s.currencyId + "'. Intro dialog " + (dialog != null ? "ready." : "disabled."));
        } else {
            economy = null;
            plugin.getLogger().severe("[Halloween] Currency '" + s.currencyId + "' on '" + s.currencyProvider
                    + "' is not available; players will not earn pumpkins until it is set up.");
        }
    }

    // ── Events ────────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Settings s = settings;
        if (!s.enabled) return;

        startTimer(player);

        if (dialog != null && !(s.dialogShowOnce && hasSeenDialog(player))) {
            player.getScheduler().runDelayed(plugin, t -> {
                if (!player.isOnline()) return;
                showDialog(player);
                player.getPersistentDataContainer().set(dialogSeenKey, PersistentDataType.BYTE, (byte) 1);
            }, null, Math.max(1, s.dialogDelayTicks));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ScheduledTask task = timers.remove(event.getPlayer().getUniqueId());
        if (task != null) task.cancel();
    }

    // ── Playtime reward ───────────────────────────────────────

    private void startTimer(Player player) {
        if (!settings.enabled || !player.isOnline()) return;
        ScheduledTask old = timers.remove(player.getUniqueId());
        if (old != null) old.cancel();

        // Ticks once per second; the entity scheduler retires the task when the player leaves.
        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, t -> tick(player), null, 20L, 20L);
        if (task != null) timers.put(player.getUniqueId(), task);
    }

    private void tick(Player player) {
        Settings s = settings;
        EconomyManager econ = economy;
        if (econ == null || !player.isOnline()) return;
        if (s.afkTimeoutSeconds > 0 && player.getIdleDuration().getSeconds() >= s.afkTimeoutSeconds) return;

        int progress = player.getPersistentDataContainer().getOrDefault(progressKey, PersistentDataType.INTEGER, 0) + 1;
        if (progress < s.intervalSeconds) {
            player.getPersistentDataContainer().set(progressKey, PersistentDataType.INTEGER, progress);
            return;
        }
        player.getPersistentDataContainer().set(progressKey, PersistentDataType.INTEGER, 0);

        if (!econ.deposit(player, s.rewardAmount)) {
            plugin.getLogger().warning("[Halloween] Could not give " + player.getName() + " their pumpkin.");
            return;
        }
        player.sendMessage(MM.deserialize(s.rewardMessage
                .replace("{amount}", NumberFormatter.format(s.rewardAmount))
                .replace("{balance}", NumberFormatter.format(econ.getBalance(player)))
                .replace("{symbol}", s.currencySymbol)));
        for (Sound sound : s.rewardSounds) {
            player.playSound(sound, Sound.Emitter.self());
        }
    }

    /** Seconds of playtime left until the player's next pumpkin. */
    public int secondsUntilNext(Player player) {
        int progress = player.getPersistentDataContainer().getOrDefault(progressKey, PersistentDataType.INTEGER, 0);
        return Math.max(0, settings.intervalSeconds - progress);
    }

    // ── Dialog ────────────────────────────────────────────────

    private boolean hasSeenDialog(Player player) {
        return player.getPersistentDataContainer().has(dialogSeenKey, PersistentDataType.BYTE);
    }

    public void showDialog(Player player) {
        Dialog d = dialog;
        if (d == null) return;
        player.showDialog(d);
        for (Sound sound : settings.dialogSounds) {
            player.playSound(sound, Sound.Emitter.self());
        }
    }

    private static Dialog buildDialog(Settings s) {
        List<DialogBody> body = new ArrayList<>();
        for (String line : s.dialogBody) {
            body.add(DialogBody.plainMessage(MM.deserialize(line), s.dialogWidth));
        }

        ActionButton open = ActionButton.builder(MM.deserialize(s.dialogOpenLabel))
                .tooltip(MM.deserialize(s.dialogOpenTooltip))
                .width(150)
                .action(DialogAction.staticAction(ClickEvent.runCommand(s.dialogOpenCommand)))
                .build();
        ActionButton close = ActionButton.builder(MM.deserialize(s.dialogCloseLabel))
                .width(150)
                .build();

        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(MM.deserialize(s.dialogTitle))
                        .canCloseWithEscape(true)
                        .body(body)
                        .build())
                .type(DialogType.confirmation(open, close)));
    }

    // ── Settings ──────────────────────────────────────────────

    private static final class Settings {
        boolean enabled;
        String currencyProvider;
        String currencyId;
        String currencySymbol;

        int intervalSeconds;
        double rewardAmount;
        int afkTimeoutSeconds;
        String rewardMessage;
        List<Sound> rewardSounds;

        boolean dialogEnabled;
        boolean dialogShowOnce;
        long dialogDelayTicks;
        int dialogWidth;
        String dialogTitle;
        List<String> dialogBody;
        String dialogOpenLabel;
        String dialogOpenTooltip;
        String dialogOpenCommand;
        String dialogCloseLabel;
        List<Sound> dialogSounds;

        static Settings load(YamlConfiguration c) {
            Settings s = new Settings();
            s.enabled = c.getBoolean("enabled", true);
            s.currencyProvider = c.getString("currency.provider", "excellenteconomy");
            s.currencyId = c.getString("currency.id", "pumpkins");
            s.currencySymbol = c.getString("currency.symbol", "🎃");

            s.intervalSeconds = Math.max(1, c.getInt("playtime-reward.interval-seconds", 300));
            s.rewardAmount = Math.max(0, c.getDouble("playtime-reward.amount", 1));
            s.afkTimeoutSeconds = Math.max(0, c.getInt("playtime-reward.afk-timeout-seconds", 300));
            s.rewardMessage = c.getString("playtime-reward.message",
                    "<#FF7518>🎃 <white>+{amount} Pumpkin! <gray>You now have <#FF7518>{balance} 🎃");
            s.rewardSounds = loadSounds(c.getConfigurationSection("playtime-reward.sounds"));

            s.dialogEnabled = c.getBoolean("intro-dialog.enabled", true);
            s.dialogShowOnce = c.getBoolean("intro-dialog.show-once", true);
            s.dialogDelayTicks = c.getLong("intro-dialog.delay-seconds", 10) * 20L;
            s.dialogWidth = Math.max(1, Math.min(1024, c.getInt("intro-dialog.width", 260)));
            s.dialogTitle = c.getString("intro-dialog.title", "<#FF7518><bold>🎃 Halloween Event</bold>");
            s.dialogBody = c.getStringList("intro-dialog.body");
            s.dialogOpenLabel = c.getString("intro-dialog.open-button.label", "<#FF7518>Open Halloween Shop");
            s.dialogOpenTooltip = c.getString("intro-dialog.open-button.tooltip", "<gray>/shop halloween");
            s.dialogOpenCommand = c.getString("intro-dialog.open-button.command", "/shop halloween");
            s.dialogCloseLabel = c.getString("intro-dialog.close-button.label", "<gray>Close");
            s.dialogSounds = loadSounds(c.getConfigurationSection("intro-dialog.sounds"));
            return s;
        }

        private static List<Sound> loadSounds(ConfigurationSection section) {
            List<Sound> sounds = new ArrayList<>();
            if (section == null) return sounds;
            for (String id : section.getKeys(false)) {
                ConfigurationSection snd = section.getConfigurationSection(id);
                if (snd == null) continue;
                try {
                    sounds.add(Sound.sound(Key.key(snd.getString("key", "")), Sound.Source.MASTER,
                            (float) snd.getDouble("volume", 1.0), (float) snd.getDouble("pitch", 1.0)));
                } catch (Exception ignored) {
                    // Invalid key: skip the sound rather than break the reward.
                }
            }
            return sounds;
        }
    }

    /** Lets {@code /halloween} show the next-pumpkin countdown in the configured format. */
    public String formatCountdown(Player player) {
        int left = secondsUntilNext(player);
        return (left / 60) + "m " + (left % 60) + "s";
    }

    public String getCurrencySymbol() {
        return settings.currencySymbol;
    }

    public EconomyManager getEconomy() {
        return economy;
    }
}
