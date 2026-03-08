/*
 * Copyright 2021 Green Mushroom
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.gm.cleaner.plugin.xposed;

import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import de.robv.android.xposed.XposedBridge;
import me.gm.cleaner.plugin.dao.JsonSharedPreferencesImpl;
import me.gm.cleaner.plugin.dao.SharedPreferencesWrapper;

public class JsonFileSpImpl extends SharedPreferencesWrapper {
    public final File file;
    protected String contentCache;

    public JsonFileSpImpl(File src) {
        file = src;

        JSONObject json;
        try {
            var str = read();
            if (TextUtils.isEmpty(str)) {
                // don't throw an exception in this case.
                json = new JSONObject();
            } else {
                json = new JSONObject(str);
            }
        } catch (JSONException e) {
            json = new JSONObject();
        }
        delegate = new JsonSharedPreferencesImpl(json);
    }

    private void ensureFile() {
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                XposedBridge.log(e);
                throw new RuntimeException(e);
            }
        }
    }

    public synchronized String read() {
        if (contentCache == null) {
            ensureFile();
            try (var it = new FileInputStream(file)) {
                var bb = ByteBuffer.allocate(it.available());
                it.getChannel().read(bb);
                contentCache = new String(bb.array());
            } catch (IOException e) {
                XposedBridge.log(e);
            }
        }
        return contentCache;
    }

    public synchronized void write(String what) {
        contentCache = what;
        try {
            delegate = new JsonSharedPreferencesImpl(new JSONObject(what));
        } catch (JSONException ignored) {
        }

        ensureFile();
        File tempFile = new File(file.getPath() + ".bak");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(what.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            fos.getFD().sync(); // 强制同步到硬件磁盘
        } catch (IOException e) {
            XposedBridge.log("Failed to write rules atomically: " + e);
            // 备份方案：如果原子写入失败，尝试直接写入（虽然不安全，但在极端文件权限下可能是唯一出路）
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(what.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (IOException ex) {
                XposedBridge.log("Critical failure writing rules: " + ex);
            }
            return;
        }

        // 原子替换
        if (!tempFile.renameTo(file)) {
            if (!file.delete() || !tempFile.renameTo(file)) {
                XposedBridge.log("Failed to rename temporary file to " + file);
            }
        }
    }
}
