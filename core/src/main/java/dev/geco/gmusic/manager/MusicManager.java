package dev.geco.gmusic.manager;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import dev.geco.gmusic.main.GMusicMain;
import dev.geco.gmusic.objects.*;
import dev.geco.gmusic.objects.MusicGUI.MenuType;
import dev.geco.gmusic.values.Values;

public class MusicManager {

    private final GMusicMain GPM;

    private ItemStack i;

    public MusicManager(GMusicMain GPluginMain) {
        GPM = GPluginMain;
    }

    public void convertSongs() {
        File baseDir = new File("plugins/" + GPM.NAME + "/");
        File songsDir = new File(baseDir, Values.SONGS_PATH);
        File convertDir = new File(baseDir, Values.CONVERT_PATH);
        File midiDir = new File(baseDir, Values.MIDI_PATH);

        if (!songsDir.exists()) songsDir.mkdirs();
        if (!convertDir.exists()) convertDir.mkdirs();
        if (!midiDir.exists()) midiDir.mkdirs();

        processDirectoryParallel(convertDir, convertDir, songsDir, Values.GNBS_FILETYP, (src, out) -> GPM.getNBSManager().convertFile(src, out));

        processDirectoryParallel(midiDir, midiDir, songsDir, Values.GNBS_FILETYP, (src, out) -> GPM.getMidiManager().convertFile(src, out));
    }

    /**
     * 递归处理目录
     */
    private void processDirectoryParallel(
            File srcBaseDir,
            File srcDir,
            File destBaseDir,
            String outputExt,
            BiConsumer<File, File> converter
    ) {
        Arrays.stream(srcDir.listFiles()).parallel().forEach(f -> {
            if (f.isDirectory()) {
                processDirectoryParallel(srcBaseDir, f, destBaseDir, outputExt, converter);
            } else {
                // 计算输出文件路径
                Path relativePath = srcBaseDir.toPath().relativize(f.toPath());
                File targetDir = new File(destBaseDir, relativePath.getParent() != null ? relativePath.getParent().toString() : "");
                if (!targetDir.exists()) targetDir.mkdirs();

                String baseName = f.getName().replaceFirst("[.][^.]+$", "");
                File outputFile = new File(targetDir, baseName + outputExt);

                if (!outputFile.exists()) {
                    converter.accept(f, outputFile);
                }
            }
        });
    }

//    public void convertSongs() {
//
//        File p = new File("plugins/" + GPM.NAME + "/" + Values.SONGS_PATH);
//
//        if (!p.exists()) p.mkdir();
//
//        File p1 = new File("plugins/" + GPM.NAME + "/" + Values.CONVERT_PATH);
//
//        if (!p1.exists()) p1.mkdir();
//
//        File p2 = new File("plugins/" + GPM.NAME + "/" + Values.MIDI_PATH);
//
//        if (!p2.exists()) p2.mkdir();
//
//        Arrays.asList(p1.listFiles()).parallelStream().forEach(f -> {
//
//            if (!new File(p.getAbsolutePath() + "/" + f.getName().replaceFirst("[.][^.]+$", "") + Values.GNBS_FILETYP).exists())
//                GPM.getNBSManager().convertFile(f);
//
//        });
//
//        Arrays.asList(p2.listFiles()).parallelStream().forEach(f -> {
//
//            if (!new File(p.getAbsolutePath() + "/" + f.getName().replaceFirst("[.][^.]+$", "") + Values.GNBS_FILETYP).exists()) GPM.getMidiManager().convertFile(f);
//
//        });
//
//    }

    public ItemStack getJukeBox() {
        return i;
    }

    public void loadMusicSettings() {
        GPM.getValues().clearSongs();
        GPM.getValues().clearDiscItems();

        File p = new File("plugins/" + GPM.NAME + "/" + Values.SONGS_PATH);
        if (!p.exists()) p.mkdir();

        try {
            loadSongsRecursive(p); // 递归加载所有子文件夹中的音乐
        } catch (Exception | Error e) {
            GPM.getLogger().log(Level.SEVERE, "Error while loading songs from " + p, e.getMessage());
        }

        GPM.getValues().sortSongs();

        // 创建 Jukebox ItemStack
        ItemStack i = new ItemStack(Material.JUKEBOX);
        ItemMeta im = i.getItemMeta();
        im.setDisplayName(GPM.getMManager().getMessage("Items.jukebox-title"));
        im.setLocalizedName(GPM.NAME + "_JB");
        List<String> iml = new ArrayList<>(Arrays.asList(GPM.getMManager().getMessage("Items.jukebox-description").split("\n")));
        im.setLore(iml);
        i.setItemMeta(im);
    }

    private void loadSongsRecursive(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;

        Arrays.stream(files).forEach(f -> {
            if (f.isDirectory()) {
                loadSongsRecursive(f); // 递归子文件夹
            } else {
                int i = f.getName().lastIndexOf(".");
                if (i > 0 && f.getName().substring(i + 1).equalsIgnoreCase(Values.GNBS_EXT)) {
                    Song s = new Song(f, GPM);
                    if (s.getNoteAmount() == 0) return;

                    ItemStack is = new ItemStack(s.getMaterial());
                    ItemMeta im = is.getItemMeta();
                    im.setDisplayName(GPM.getMManager().getMessage(
                            "Items.disc-title",
                            "%Title%", s.getTitle(),
                            "%Author%", s.getAuthor().isEmpty() ? GPM.getMManager().getMessage("MusicGUI.disc-empty-author") : s.getAuthor(),
                            "%OAuthor%", s.getOriginalAuthor().isEmpty() ? GPM.getMManager().getMessage("MusicGUI.disc-empty-oauthor") : s.getOriginalAuthor()
                    ));
                    im.setLocalizedName(GPM.NAME + "_D_" + s.getId());

                    List<String> dl = new ArrayList<>();
                    for (String d : s.getDescription()) dl.add(GPM.getMManager().getColoredMessage("&6" + d));
                    im.setLore(dl);
                    im.addItemFlags(ItemFlag.values());

                    is.setItemMeta(im);

                    GPM.getValues().putDiscItem(is, s);
                    GPM.getValues().addSong(s);
                }
            }
        });
    }

//    public void loadMusicSettings() {
//
//        GPM.getValues().clearSongs();
//
//        GPM.getValues().clearDiscItems();
//
//        File p = new File("plugins/" + GPM.NAME + "/" + Values.SONGS_PATH);
//
//        if (!p.exists()) p.mkdir();
//
//        try {
//
//            Arrays.asList(p.listFiles()).parallelStream().forEach(f -> {
//
//                int i = f.getName().lastIndexOf(".");
//                if (i > 0 && f.getName().substring(i + 1).equalsIgnoreCase(Values.GNBS_EXT)) {
//
//                    Song s = new Song(f);
//
//                    if (s.getNoteAmount() == 0) return;
//
//                    ItemStack is = new ItemStack(s.getMaterial());
//
//                    ItemMeta im = is.getItemMeta();
//
//                    im.setDisplayName(GPM.getMManager().getMessage("Items.disc-title", "%Title%", s.getTitle(), "%Author%", s.getAuthor().equals("") ? GPM.getMManager().getMessage("MusicGUI.disc-empty-author") : s.getAuthor(), "%OAuthor%", s.getOriginalAuthor().equals("") ? GPM.getMManager().getMessage("MusicGUI.disc-empty-oauthor") : s.getOriginalAuthor()));
//
//                    im.setLocalizedName(GPM.NAME + "_D_" + s.getId());
//
//                    List<String> dl = new ArrayList<>();
//
//                    for (String d : s.getDescription()) dl.add(GPM.getMManager().getColoredMessage("&6" + d));
//
//                    im.setLore(dl);
//
//                    im.addItemFlags(ItemFlag.values());
//
//                    is.setItemMeta(im);
//
//                    GPM.getValues().putDiscItem(is, s);
//
//                    GPM.getValues().addSong(s);
//
//                }
//
//            });
//
//        } catch (Exception | Error e) {
//            e.printStackTrace();
//        }
//
//        GPM.getValues().sortSongs();
//
//        i = new ItemStack(Material.JUKEBOX);
//        ItemMeta im = i.getItemMeta();
//        im.setDisplayName(GPM.getMManager().getMessage("Items.jukebox-title"));
//        im.setLocalizedName(GPM.NAME + "_JB");
//        List<String> iml = new ArrayList<>();
//        for (String imlr : GPM.getMManager().getMessage("Items.jukebox-description").split("\n")) iml.add(imlr);
//        im.setLore(iml);
//        i.setItemMeta(im);
//
//    }

    public MusicGUI getMusicGUI(UUID U, MenuType MenuType) {

        MusicGUI r = GPM.getValues().getMusicGUIs().get(U);

        return r != null ? r : new MusicGUI(U, MenuType, GPM);

    }

}