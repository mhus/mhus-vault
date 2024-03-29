/**
 * Copyright (C) 2018 Mike Hummel (mh@mhus.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.mhus.app.vault.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import de.mhus.app.vault.api.model.VaultEntry;
import de.mhus.app.vault.api.model.VaultGroup;
import de.mhus.app.vault.api.model.VaultKey;
import de.mhus.app.vault.api.model.VaultTarget;
import de.mhus.lib.adb.query.Db;
import de.mhus.lib.basics.RC;
import de.mhus.lib.core.MProperties;
import de.mhus.lib.core.MString;
import de.mhus.lib.core.pojo.MPojo;
import de.mhus.lib.core.pojo.PojoModelFactory;
import de.mhus.lib.errors.MException;
import de.mhus.crypt.api.util.CryptUtil;

public class ExportUtil {

    @SuppressWarnings("unused")
    private File file;

    private String publicKey;
    private String group;
    private ZipOutputStream zip;
    private PojoModelFactory factory;

    public void exportDb(String publicKey, String file, String group)
            throws IOException, MException {
        this.publicKey = publicKey;
        this.file = new File(file);
        this.group = group;

        if (CryptUtil.getCipher(publicKey) == null)
            throw new MException(RC.ERROR, "cipher not found for public key");

        factory = StaticAccess.db.getManager().getPojoModelFactory();

        FileOutputStream fos = new FileOutputStream(file);
        zip = new ZipOutputStream(fos);

        // remember the key
        savePlain("public_key", publicKey);

        // remember metadata
        MProperties prop = new MProperties();
        prop.setString("group", group);
        prop.setString("type", "export");
        savePlain("meta.properties", prop.saveToString());

        // exportAllGroups
        exportGroups();

        // exportAllTargets
        exportTargets();

        // exportEntries
        exportEntries();

        // export MVault entries (CherryMVaultSource only)
        exportVault();

        zip.close();
        fos.close();
    }

    private void exportVault() throws MException, IOException {
        for (VaultKey key : StaticAccess.db.getManager().getAll(VaultKey.class)) {
            System.out.println(">>> Save Key " + key);
            String content = MPojo.objectToBase64(key, factory);
            save("key/" + key.getId(), content);
        }
    }

    private void exportEntries() throws MException, IOException {
        for (VaultEntry entry :
                group == null
                        ? StaticAccess.db.getManager().getAll(VaultEntry.class)
                        : StaticAccess.db
                                .getManager()
                                .getByQualification(
                                        Db.query(VaultEntry.class).eq("group", group))) {
            System.out.println(">>> Save Entry " + entry);
            String content = MPojo.objectToBase64(entry, factory);
            save("entry/" + entry.getId(), content);
        }
    }

    private void exportTargets() throws MException, IOException {
        for (VaultTarget target : StaticAccess.db.getManager().getAll(VaultTarget.class)) {
            System.out.println(">>> Save Target " + target);
            String content = MPojo.objectToBase64(target, factory);
            save("target/" + target.getId(), content);
        }
    }

    private void exportGroups() throws MException, IOException {
        for (VaultGroup group : StaticAccess.db.getManager().getAll(VaultGroup.class)) {
            System.out.println(">>> Save Group " + group);
            String content = MPojo.objectToBase64(group, factory);
            save("group/" + group.getId(), content);
        }
    }

    private void savePlain(String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zip.putNextEntry(entry);
        zip.write(content.getBytes(MString.CHARSET_CHARSET_UTF_8));
        zip.closeEntry();
    }

    private void save(String name, String content) throws IOException, MException {
        content = CryptUtil.encrypt(publicKey, content);
        savePlain(name, content);
    }
}
