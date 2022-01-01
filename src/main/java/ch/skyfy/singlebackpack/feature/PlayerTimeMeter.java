package ch.skyfy.singlebackpack.feature;


import ch.skyfy.singlebackpack.SingleBackpack;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static ch.skyfy.singlebackpack.SingleBackpack.DISABLED;


public class PlayerTimeMeter {

    private static class PlayerTimeMeterHolder {
        public static final PlayerTimeMeter INSTANCE = new PlayerTimeMeter();
    }

    @SuppressWarnings("UnusedReturnValue")
    public static PlayerTimeMeter getInstance() {
        return PlayerTimeMeterHolder.INSTANCE;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void initialize() {
        getInstance();
    }

    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private TimeChangedEvent event;

    private final List<PlayerTime> playerTimes;

    private final File playerTimesFolder;

    public PlayerTimeMeter() {
        playerTimesFolder = createConfigDir();
        playerTimes = new ArrayList<>();
        registerEvent();
        startSaverTimer();
    }

    private File createConfigDir() {
        var playerTimesFolder = SingleBackpack.MOD_CONFIG_DIR.resolve("playerTimes").toFile();
        if (!playerTimesFolder.exists())
            if (!playerTimesFolder.mkdir()) DISABLED.set(true);
        return playerTimesFolder;
    }

    private void registerEvent() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (playerTimes.stream().noneMatch(playerTime -> playerTime.uuid.equals(handler.player.getUuidAsString()))) {
                playerTimes.add(new PlayerTime(handler.player.getUuidAsString(), System.currentTimeMillis()));
                fireTimeChangedEvent(handler.player.getUuidAsString());
                var nbt = new NbtCompound();
                nbt.putLong(handler.player.getUuidAsString(), getTime(handler.player.getUuidAsString()));
                sender.sendPacket(new Identifier("test"), PacketByteBufs.create().writeNbt(nbt));
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            playerTimes.forEach(playerTime -> {
                if (playerTime.uuid.equals(handler.player.getUuidAsString()))
                    playerTime.saveTimeToDisk();
            });
            playerTimes.removeIf(playerTime -> playerTime.uuid.equals(handler.player.getUuidAsString()));
        });
    }

    private void startSaverTimer() {
        new Timer(true).schedule(new TimerTask() {
            private int count = 0;

            @Override
            public void run() {
                for (var playerTime : playerTimes) {
                    playerTime.saveTime();
                    fireTimeChangedEvent(playerTime.uuid);
                }
                if (count >= 360) {
                    playerTimes.forEach(PlayerTime::saveTimeToDisk);
                    count = 0;
                }
                count++;
            }
        }, 5000, 5000);
    }

    public void registerTimeChangedEvent(TimeChangedEvent event) {
        this.event = event;
    }

    public void fireTimeChangedEvent(String uuid) {
        if (event != null) event.timeChanged(uuid, getTime(uuid));
    }

    private void saveTimeForSpecificPlayer(String uuid) {
        playerTimes.stream().filter(playerTime -> playerTime.uuid.equals(uuid)).findFirst().ifPresent(PlayerTime::saveTime);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public Long getTime(String uuid) {
        saveTimeForSpecificPlayer(uuid);
        return playerTimes.stream().filter(playerTime -> playerTime.uuid.equals(uuid)).findFirst().get().time;
    }

    private static void save(File file, Long r) throws IOException {
        try (var writer = new FileWriter(file)) {
            gson.toJson(r, Long.TYPE, writer);
        }
    }

    private static void get(File file) throws IOException {
        try (var reader = new FileReader(file)) {
            gson.fromJson(reader, (Type) Long.TYPE);
        }
    }

    static class PlayerTime {
        final String uuid;
        private Long startTime, time;
        private final File file;

        public PlayerTime(String uuid, Long startTime) {
            this.uuid = uuid;
            this.startTime = startTime;
            file = getInstance().playerTimesFolder.toPath().resolve(uuid + ".json").toFile();
            this.time = getTime();
        }

        private Long getTime() {
            if (file.exists()) {
                try {
                    get(file);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return 0L;
        }

        public Long saveTime() {
            var elapsed = System.currentTimeMillis() - startTime;
            time += elapsed;
            System.out.println("Temps écoulé: " + elapsed);
            System.out.println("Temps total: " + time);
            startTime = System.currentTimeMillis();
            return time;
        }
        public void saveTimeToDisk(){
            try {
                save(file, saveTime());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public interface TimeChangedEvent {
        void timeChanged(String uuid, Long time);
    }

}
