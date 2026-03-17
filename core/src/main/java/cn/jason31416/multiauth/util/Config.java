package cn.jason31416.multiauth.util;

import cn.jason31416.multiauth.MultiAuth;
import lombok.Getter;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.Objects;

public class Config {
    @Getter
    private static volatile MapTree configTree;

    public static void init() {
        if(!MultiAuth.getInstance().getDataDirectory().exists()) MultiAuth.getInstance().getDataDirectory().mkdirs();
        File config = new File(MultiAuth.getInstance().getDataDirectory(), "config.yml");
        if(!config.exists()){
            try (InputStream is = MultiAuth.class.getClassLoader().getResourceAsStream("config.yml"); OutputStream os = new FileOutputStream(config)) {
                Objects.requireNonNull(is).transferTo(os);
            }catch (Exception e){
                Logger.error("Cannot save config file!");
                throw new RuntimeException(e);
            }
        }

        try(InputStream inputStream = new FileInputStream(config)) {
            configTree = new MapTree(new Yaml().load(inputStream));
        }catch (Exception e){
            Logger.error("Failed to load config.yml: " + e.getMessage());
        }
    }

    public static Object getItem(String key) {
        return configTree.get(key);
    }

    public static MapTree getSection(String key){
        return configTree.getSection(key);
    }
    public static int getInt(String key){
        return configTree.getInt(key);
    }
    public static double getDouble(String key){
        return configTree.getDouble(key);
    }
    public static String getString(String key){
        return configTree.getString(key);
    }
    public static boolean getBoolean(String key){
        return configTree.getBoolean(key);
    }
    public static boolean contains(String key){
        return configTree.contains(key);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void reload() {
        if(!MultiAuth.getInstance().getDataDirectory().exists()) MultiAuth.getInstance().getDataDirectory().mkdirs();

        File config = new File(MultiAuth.getInstance().getDataDirectory(), "config.yml");
        if(!config.exists()){
            try (InputStream is = MultiAuth.class.getClassLoader().getResourceAsStream("config.yml"); OutputStream os = new FileOutputStream(config)) {
                Objects.requireNonNull(is).transferTo(os);
            } catch(Exception e) {
                throw new RuntimeException(e);
            }
        }

        try(InputStream is = new FileInputStream(config)){
            configTree = new MapTree(new Yaml().load(is));
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
}
