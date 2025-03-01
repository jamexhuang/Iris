/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2021 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.volmit.iris.engine.data.loader;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gson.Gson;
import com.volmit.iris.Iris;
import com.volmit.iris.core.IrisDataManager;
import com.volmit.iris.engine.object.IrisRegistrant;
import com.volmit.iris.util.collection.KList;
import com.volmit.iris.util.collection.KMap;
import com.volmit.iris.util.collection.KSet;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.io.IO;
import com.volmit.iris.util.scheduling.ChronoLatch;
import com.volmit.iris.util.scheduling.IrisLock;
import com.volmit.iris.util.scheduling.J;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;
import lombok.Data;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

@Data
public class ResourceLoader<T extends IrisRegistrant> {
    public static final AtomicDouble tlt = new AtomicDouble(0);
    protected File root;
    protected String folderName;
    protected String resourceTypeName;
    protected KMap<String, File> folderMapCache;
    protected KMap<String, T> loadCache;
    protected KList<File> folderCache;
    protected Class<? extends T> objectClass;
    protected String cname;
    protected IrisLock lock;
    protected String[] possibleKeys = null;
    protected IrisDataManager manager;
    protected AtomicInteger loads;
    protected ChronoLatch sec;

    public ResourceLoader(File root, IrisDataManager manager, String folderName, String resourceTypeName, Class<? extends T> objectClass) {
        lock = new IrisLock("Res");
        this.manager = manager;
        sec = new ChronoLatch(5000);
        loads = new AtomicInteger();
        folderMapCache = new KMap<>();
        this.objectClass = objectClass;
        cname = objectClass.getCanonicalName();
        this.resourceTypeName = resourceTypeName;
        this.root = root;
        this.folderName = folderName;
        loadCache = new KMap<>();
        Iris.debug("Loader<" + C.GREEN + resourceTypeName + C.LIGHT_PURPLE + "> created in " + C.RED + "IDM/" + manager.getId() + C.LIGHT_PURPLE + " on " + C.WHITE + manager.getDataFolder().getPath());
    }

    public void logLoad(File path, T t) {
        loads.getAndIncrement();

        if (loads.get() == 1) {
            sec.flip();
        }

        if (sec.flip()) {
            J.a(() -> {
                Iris.verbose("Loaded " + C.WHITE + loads.get() + " " + resourceTypeName + (loads.get() == 1 ? "" : "s") + C.GRAY + " (" + Form.f(getLoadCache().size()) + " " + resourceTypeName + (loadCache.size() == 1 ? "" : "s") + " Loaded)");
                loads.set(0);
            });
        }

        Iris.debug("Loader<" + C.GREEN + resourceTypeName + C.LIGHT_PURPLE + "> iload " + C.YELLOW + t.getLoadKey() + C.LIGHT_PURPLE + " in " + C.GRAY + t.getLoadFile().getPath() + C.LIGHT_PURPLE + " TLT: " + C.RED + Form.duration(tlt.get(), 2));
    }

    public void failLoad(File path, Throwable e) {
        J.a(() -> Iris.warn("Couldn't Load " + resourceTypeName + " file: " + path.getPath() + ": " + e.getMessage()));
    }

    private KList<File> matchAllFiles(File root, Predicate<File> f)
    {
        KList<File> fx = new KList<>();
        matchFiles(root, fx, f);
        return fx;
    }

    private void matchFiles(File at, KList<File> files, Predicate<File> f)
    {
        if(at.isDirectory())
        {
            for(File i : at.listFiles())
            {
                matchFiles(i, files, f);
            }
        }

        else
        {
            if(f.test(at))
            {
                files.add(at);
            }
        }
    }

    public String[] getPossibleKeys() {
        if (possibleKeys != null) {
            return possibleKeys;
        }

        Iris.info("Building " + resourceTypeName + " Registry Lists");
        KSet<String> m = new KSet<>();

        for(File i : getFolders())
        {
            for(File j : matchAllFiles(i, (f) -> f.getName().endsWith(".json")))
            {
                m.add(i.toURI().relativize(j.toURI()).getPath().replaceAll("\\Q.json\\E", ""));
            }
        }

        KList<String> v = new KList<>(m);
        possibleKeys = v.toArray(new String[0]);
        return possibleKeys;
    }

    public long count() {
        return loadCache.size();
    }

    protected T loadFile(File j, String key, String name) {
        try {
            PrecisionStopwatch p = PrecisionStopwatch.start();
            T t = new Gson().fromJson(IO.readAll(j), objectClass);
            loadCache.put(key, t);
            t.setLoadKey(name);
            t.setLoadFile(j);
            t.setLoader(manager);
            logLoad(j, t);
            lock.unlock();
            tlt.addAndGet(p.getMilliseconds());
            return t;
        } catch (Throwable e) {
            Iris.reportError(e);
            lock.unlock();
            failLoad(j, e);
            return null;
        }
    }

    public KList<T> loadAll(KList<String> s) {
        KList<T> m = new KList<>();

        for (String i : s) {
            T t = load(i);

            if (t != null) {
                m.add(t);
            }
        }

        return m;
    }

    public T load(String name) {
        return load(name, true);
    }

    public T load(String name, boolean warn) {
        if (name == null) {
            return null;
        }

        if (name.trim().isEmpty()) {
            return null;
        }

        String key = name + "-" + cname;

        if (loadCache.containsKey(key)) {
            return loadCache.get(key);
        }

        lock.lock();
        for (File i : getFolders(name)) {
            for (File j : i.listFiles()) {
                if (j.isFile() && j.getName().endsWith(".json") && j.getName().split("\\Q.\\E")[0].equals(name)) {
                    return loadFile(j, key, name);
                }
            }

            File file = new File(i, name + ".json");

            if (file.exists()) {
                return loadFile(file, key, name);
            }
        }

        if (warn && !resourceTypeName.equals("Dimension")) {
            J.a(() -> Iris.warn("Couldn't find " + resourceTypeName + ": " + name));
        }

        lock.unlock();
        return null;
    }

    public KList<File> getFolders() {
        lock.lock();
        if (folderCache == null) {
            folderCache = new KList<>();

            for (File i : root.listFiles()) {
                if (i.isDirectory()) {
                    if (i.getName().equals(folderName)) {
                        folderCache.add(i);
                        break;
                    }
                }
            }
        }

        lock.unlock();

        return folderCache;
    }

    public KList<File> getFolders(String rc) {
        KList<File> folders = getFolders().copy();

        if (rc.contains(":")) {
            for (File i : folders.copy()) {
                if (!rc.startsWith(i.getName() + ":")) {
                    folders.remove(i);
                }
            }
        }

        return folders;
    }

    public void clearCache() {
        lock.lock();
        possibleKeys = null;
        loadCache.clear();
        folderCache = null;
        lock.unlock();
    }

    public File fileFor(T b) {
        for (File i : getFolders()) {
            for (File j : i.listFiles()) {
                if (j.isFile() && j.getName().endsWith(".json") && j.getName().split("\\Q.\\E")[0].equals(b.getLoadKey())) {
                    return j;
                }
            }

            File file = new File(i, b.getLoadKey() + ".json");

            if (file.exists()) {
                return file;
            }
        }

        return null;
    }

    public boolean isLoaded(String next) {
        return loadCache.containsKey(next);
    }

    public void clearList() {
        lock.lock();
        folderCache = null;
        possibleKeys = null;
        lock.unlock();
    }

    public KList<String> getPossibleKeys(String arg) {
        KList<String> f = new KList<>();

        for(String i : getPossibleKeys())
        {
            if(i.equalsIgnoreCase(arg) || i.toLowerCase(Locale.ROOT).startsWith(arg.toLowerCase(Locale.ROOT)) || i.toLowerCase(Locale.ROOT).contains(arg.toLowerCase(Locale.ROOT)) || arg.toLowerCase(Locale.ROOT).contains(i.toLowerCase(Locale.ROOT)))
            {
                f.add(i);
            }
        }

        return f;
    }
}
