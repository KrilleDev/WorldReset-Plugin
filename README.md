# 🌍 WorldReset

> A lightweight and configurable Minecraft Paper plugin that automatically resets worlds while keeping your server clean and fresh.

![Minecraft](https://img.shields.io/badge/Minecraft-1.21+-brightgreen)
![Paper](https://img.shields.io/badge/Paper-Supported-blue)
![Java](https://img.shields.io/badge/Java-21+-orange)
![License](https://img.shields.io/badge/License-MIT-green)

---

## ✨ Features

- 🌍 Reset any world with a single command
- 🌱 Generates a completely new random seed on every reset
- 💾 Automatic world backup (optional)
- ⚡ Fast and safe world deletion
- 🔄 Automatically recreates the world
- 📦 Lightweight with minimal performance impact
- ⚙️ Easy configuration
- 🔐 Permission support

---

## 📥 Installation

1. Download the latest release.
2. Place `WorldReset.jar` into your server's `plugins` folder.
3. Restart the server.
4. Configure the plugin in the generated config files.

---

## 📖 Commands

| Command | Description |
|---------|-------------|
| `/worldreset <world>` | Resets the specified world |
| `/worldreset reload` | Reloads the configuration |
| `/worldreset confirm` | Confirms a pending reset |

---

## 🔑 Permissions

| Permission | Description |
|------------|-------------|
| `worldreset.admin` | Access to all commands |
| `worldreset.reset` | Reset worlds |
| `worldreset.reload` | Reload the plugin |

---

## ⚙️ Configuration

Example:

```yaml
backup:
  enabled: true

seed:
  random: true

messages:
  prefix: "&aWorldReset &8»"
```

---

## 🔄 How it works

When a world reset is started the plugin:

1. Saves the world
2. Unloads it safely
3. Creates a backup (optional)
4. Deletes the world files
5. Generates a new world with a **new random seed**
6. Loads the new world automatically
7. Teleports players if configured

---

## ✅ Supported Versions

- Paper 1.21+
- Purpur *(should work)*
- Folia *(experimental)*

---

## 🛠️ Building

```bash
git clone https://github.com/KrilleDev/WorldReset-Plugin.git
cd WorldReset-Plugin
./gradlew build
```

The compiled jar will be located in:

```
build/libs/
```

---

## 📌 Roadmap

- [ ] GUI
- [ ] Scheduled resets
- [ ] PlaceholderAPI support
- [ ] Multi-world support
- [ ] Async backups
- [ ] Webhook logging

---

## ❤️ Contributing

Pull Requests are welcome!

If you find a bug or have an idea, feel free to open an Issue.

---

## 📄 License

This project is licensed under the MIT License.

---

Made by **KrilleDev**
