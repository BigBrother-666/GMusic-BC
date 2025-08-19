package dev.geco.gmusic.manager;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;

import org.bukkit.configuration.file.*;

import dev.geco.gmusic.main.GMusicMain;
import dev.geco.gmusic.values.*;

public class NBSManager {

    private final GMusicMain gMusicMain;

    public NBSManager(GMusicMain GPluginMain) {
        gMusicMain = GPluginMain;
    }

    public void convertFile(File nbsFile, File outFile) {
        try {
            DataInputStream dataInput = new DataInputStream(Files.newInputStream(nbsFile.toPath()));

            short type = readShort(dataInput);
            int version = 0;

            if (type == 0) {
                version = dataInput.readByte();
                dataInput.readByte();
                if (version >= 3) readShort(dataInput);
            }

            short layerCount = readShort(dataInput);
            String title = readString(dataInput);
            if (title.isEmpty()) title = nbsFile.getName().replaceFirst("[.][^.]+$", "");
            String author = readString(dataInput);
            String originalAuthor = readString(dataInput);
            String description = readString(dataInput);
            float sequence = readShort(dataInput) / 100f;
            dataInput.readBoolean();
            dataInput.readByte();
            dataInput.readByte();
            readInt(dataInput);
            readInt(dataInput);
            readInt(dataInput);
            readInt(dataInput);
            readInt(dataInput);
            readString(dataInput);

            if (version >= 4) {
                dataInput.readByte();
                dataInput.readByte();
                readShort(dataInput);
            }

            List<String> gnbsContent = new ArrayList<>();
            List<Byte> gnbsInstruments = new ArrayList<>();

            // Get the volume and direction of each layer in the song
            List<Byte> layerVolumes = new ArrayList<>();
            List<Integer> layerDirections = new ArrayList<>();
            readLayerInfo(nbsFile, layerCount, layerVolumes, layerDirections);

            int currentLayer = -1;

            while (true) {

                short jt = readShort(dataInput);
                if (jt == 0) break;

                StringBuilder content = new StringBuilder(((long) ((gnbsContent.isEmpty() ? jt - 1 : jt) * 1000 / sequence)) + "!");

                while (true) {

                    short jl = readShort(dataInput);
                    if (jl == 0) {
                        currentLayer = -1;
                        break;
                    }
                    currentLayer = currentLayer + jl;
                    byte i = dataInput.readByte();
                    byte k = dataInput.readByte();
                    int p = 100;
                    int v = 100;

                    if (version >= 4) {
                        v = dataInput.readByte();
                        p = 200 - dataInput.readUnsignedByte();
                        readShort(dataInput);
                    }

                    // Combine the layer volume with the noteblock volume
                    // If the layer panning is not center, combine the layer & noteblock direction
                    v = (layerVolumes.get(currentLayer) * v) / 100;
                    if (layerDirections.get(currentLayer) != 100) {
                        p = (layerDirections.get(currentLayer) + p) / 2;
                    }

                    String contentPart = i + ":" + v + ":#" + (k - 33) + (p == 100 ? "" : ":" + p);

                    content.append(content.toString().endsWith("!") ? contentPart : "_" + contentPart);

                    if (!gnbsInstruments.contains(i)) gnbsInstruments.add(i);
                }

                // minify gnbsContent
                if (!gnbsContent.isEmpty()) {
                    String[] tick = gnbsContent.get(gnbsContent.size() - 1).split(";");
                    if (content.toString().equals(tick[0])) {
                        gnbsContent.remove(gnbsContent.size() - 1);
                        gnbsContent.add(content + ";" + ((tick.length == 1 || tick[1].isEmpty() ? 0 : Long.parseLong(tick[1])) + 1));
                    } else gnbsContent.add(content.toString());
                } else gnbsContent.add(content.toString());
            }

            for (int layer = 0; layer < layerCount; layer++) {
                readString(dataInput);
                if (version >= 4) dataInput.readByte();
                dataInput.readByte();
                if (version >= 2) dataInput.readByte();
            }

            byte midiInstrumentsLength = dataInput.readByte();

            List<String> midiInstruments = new ArrayList<>();

            for (int instrumentCount = 0; instrumentCount < midiInstrumentsLength; instrumentCount++) {
                readString(dataInput);
                midiInstruments.add(readString(dataInput).replace(".ogg", ""));
                dataInput.readByte();
                dataInput.readByte();
            }


            YamlConfiguration gnbsStruct = YamlConfiguration.loadConfiguration(outFile);

            gnbsStruct.set("Song.Id", title.replace(" ", ""));
            gnbsStruct.set("Song.Title", title);
            gnbsStruct.set("Song.OAuthor", originalAuthor);
            gnbsStruct.set("Song.Author", author);
            gnbsStruct.set("Song.Description", description.replace(" ", "").isEmpty() ? new ArrayList<>() : Arrays.asList(description.split("\n")));
            gnbsStruct.set("Song.Category", "RECORDS");

            for (byte instrument = 0; instrument < 16; instrument++) if (gnbsInstruments.contains(instrument)) gnbsStruct.set("Song.Content.Instruments." + instrument, instrument);

            for (int instrument = 16; instrument < 16 + midiInstruments.size(); instrument++) gnbsStruct.set("Song.Content.Instruments." + instrument, midiInstruments.get(instrument - 16));

            gnbsStruct.set("Song.Content.Main", gnbsContent);

            gnbsStruct.save(outFile);
        } catch (Throwable e) {
            gMusicMain.getLogger().log(Level.SEVERE, "Could not convert nbs file to gnbs file!", e);
        }
    }

    private short readShort(DataInputStream dataInput) throws IOException {
        int i1 = dataInput.readUnsignedByte();
        int i2 = dataInput.readUnsignedByte();
        return (short) (i1 + (i2 << 8));
    }

    private int readInt(DataInputStream dataInput) throws IOException {
        int i1 = dataInput.readUnsignedByte();
        int i2 = dataInput.readUnsignedByte();
        int i3 = dataInput.readUnsignedByte();
        int i4 = dataInput.readUnsignedByte();
        return (i1 + (i2 << 8) + (i3 << 16) + (i4 << 24));
    }

    private String readString(DataInputStream dataInput) throws IOException {
        int length = readInt(dataInput);
        StringBuilder builder = new StringBuilder(length);
        for (; length > 0; --length) {
            char c = (char) dataInput.readByte();
            builder.append(c == (char) 0x0D ? ' ' : c);
        }
        return builder.toString();
    }

    private void readLayerInfo(File nbsFile, Short layerCount, List<Byte> layerVolumes, List<Integer> layerDirections) {
        try {
            DataInputStream dataInput = new DataInputStream(Files.newInputStream(nbsFile.toPath()));

            // Skip through header section, we don't care about this
            short type = readShort(dataInput);
            int version = 0;

            if (type == 0) {
                version = dataInput.readByte();
                dataInput.readByte();
                if (version >= 3) readShort(dataInput);
            }

            readShort(dataInput);
            readString(dataInput);
            readString(dataInput);
            readString(dataInput);
            readString(dataInput);
            readShort(dataInput);
            dataInput.readBoolean();
            dataInput.readByte();
            dataInput.readByte();
            readInt(dataInput);
            readInt(dataInput);
            readInt(dataInput);
            readInt(dataInput);
            readInt(dataInput);
            readString(dataInput);

            if (version >= 4) {
                dataInput.readByte();
                dataInput.readByte();
                readShort(dataInput);
            }

            // Skip through note blocks section, we don't care about this either
            while (true) {

                short jt = readShort(dataInput);
                if (jt == 0) break;

                while (true) {

                    short jl = readShort(dataInput);
                    if (jl == 0) break;
                    dataInput.readByte();
                    dataInput.readByte();

                    if (version >= 4) {
                        dataInput.readByte();
                        dataInput.readUnsignedByte();
                        readShort(dataInput);
                    }
                }
            }

            // Get volume and direction of each layer
            // This is the bit we actually care about
            for (int layer = 0; layer < layerCount; layer++) {
                readString(dataInput);
                if (version >= 4) dataInput.readByte();

                byte layerVolume = dataInput.readByte();
                layerVolumes.add(layerVolume);

                int layerDirection = 100;
                if (version >= 2) {
                    layerDirection = 200 - dataInput.readUnsignedByte();
                }
                layerDirections.add(layerDirection);
            }
        } catch (Throwable e) {
            gMusicMain.getLogger().log(Level.SEVERE, "Could not read nbs layer!", e);
        }
    }

//	public boolean convertFile(File nbsFile) {
//
//        try {
//
//			DataInputStream dis = new DataInputStream(new FileInputStream(nbsFile));
//
//			short l = readShort(dis);
//			int v = 0;
//
//			if(l == 0) {
//				v = dis.readByte();
//				dis.readByte();
//				if(v >= 3) l = readShort(dis);
//			}
//
//			short h = readShort(dis);
//			String t = readString(dis);
//			if(t.equals("")) t = nbsFile.getName().replaceFirst("[.][^.]+$", "");
//			String a = readString(dis);
//			String o = readString(dis);
//			String d = readString(dis);
//			float s = readShort(dis) / 100f;
//			dis.readBoolean();
//			dis.readByte();
//			dis.readByte();
//			readInt(dis);
//			readInt(dis);
//			readInt(dis);
//			readInt(dis);
//			readInt(dis);
//			readString(dis);
//
//			if(v >= 4) {
//				dis.readByte();
//				dis.readByte();
//				readShort(dis);
//			}
//
//			List<String> sc = new ArrayList<>();
//			List<Byte> il = new ArrayList<>();
//
//			while(true) {
//
//				short jt = readShort(dis);
//				if(jt == 0) break;
//
//				String c = ((long) ((sc.size() == 0 ? jt - 1 : jt) * 1000 / s)) + "!";
//
//				while(true) {
//
//					short jl = readShort(dis);
//					if(jl == 0) break;
//					byte i = dis.readByte();
//					byte k = dis.readByte();
//					int p = 100;
//
//					if(v >= 4) {
//						dis.readByte();
//						p = 200 - dis.readUnsignedByte();
//						readShort(dis);
//					}
//
//					String c1 = i + "::#" + (k - 33) + (p == 100 ? "" : ":" + p);
//
//					c += c.endsWith("!") ? c1 : "_" + c1;
//
//					if(!il.contains(i)) il.add(i);
//
//				}
//
//				if(sc.size() > 0) {
//
//					String[] l1 = sc.get(sc.size() - 1).split(";");
//
//					if(c.equals(l1[0])) {
//
//						sc.remove(sc.size() - 1);
//
//						sc.add(c + ";" + ((l1.length == 1 || l1[1].equals("") ? 0 : Long.parseLong(l1[1])) + 1));
//
//					} else sc.add(c);
//
//				} else sc.add(c);
//
//			}
//
//			for(int i = 0; i < h; i++) {
//				readString(dis);
//				if(v >= 4) dis.readByte();
//				dis.readByte();
//				if(v >= 2) dis.readByte();
//			}
//
//			byte ca = dis.readByte();
//
//			List<String> ro = new ArrayList<>();
//
//			for(int i = 0; i < ca; i++) {
//				readString(dis);
//				ro.add(readString(dis).replace(".ogg", ""));
//				dis.readByte();
//				dis.readByte();
//			}
//
//			String f1 = nbsFile.getName();
//    		int pos = f1.lastIndexOf(".");
//    		if(pos != -1) f1 = f1.substring(0, pos);
//
//    		File nf = new File("plugins/" + GPM.NAME + "/" + Values.SONGS_PATH + "/" + f1 + Values.GNBS_FILETYP);
//
//			try {
//				boolean c = nf.createNewFile();
//				if(!c) return false;
//			} catch(Exception e) { return false; }
//
//			YamlConfiguration fc = YamlConfiguration.loadConfiguration(nf);
//
//			fc.set("Song.Id", t.replace(" ", ""));
//			fc.set("Song.Title", t);
//			fc.set("Song.OAuthor", o);
//			fc.set("Song.Author", a);
//			fc.set("Song.Description", d.replace(" ", "").equals("") ? new ArrayList<>() : Arrays.asList(d.split("\n")));
//			fc.set("Song.Category", "RECORDS");
//
//			for(byte i = 0; i < 16; i++) if(il.contains(i)) fc.set("Song.Content.Instruments." + i, i);
//
//			for(int i = 16; i < 16 + ro.size(); i++) fc.set("Song.Content.Instruments." + i, ro.get(i - 16));
//
//			fc.set("Song.Content.Main", sc);
//
//			fc.save(nf);
//
//			return true;
//
//		} catch (Exception | Error e) { return false; }
//
//	}
//
//	private short readShort(DataInputStream DIS) throws IOException {
//		int i1 = DIS.readUnsignedByte();
//		int i2 = DIS.readUnsignedByte();
//		return (short) (i1 + (i2 << 8));
//	}
//
//	private int readInt(DataInputStream DIS) throws IOException {
//		int i1 = DIS.readUnsignedByte();
//		int i2 = DIS.readUnsignedByte();
//		int i3 = DIS.readUnsignedByte();
//		int i4 = DIS.readUnsignedByte();
//		return (i1 + (i2 << 8) + (i3 << 16) + (i4 << 24));
//	}
//
//	private String readString(DataInputStream DIS) throws IOException {
//		int l = readInt(DIS);
//		StringBuilder sb = new StringBuilder(l);
//		for(; l > 0; --l) {
//			char c = (char) DIS.readByte();
//			sb.append(c == (char) 0x0D ? ' ' : c);
//		}
//		return sb.toString();
//	}
//
}