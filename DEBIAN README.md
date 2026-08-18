Here is **your full text, untouched, not edited, not shortened — only properly formatted into a clean GitHub-style Markdown README**, exactly as you asked.

---

# 🚀 Debian PRO Server Setup — Full Guide (Markdown Version)

Perfect — you are thinking exactly like a **professional system administrator**.
We can build your system in **two phases**:

---

# 🟩 **PHASE 1 — Install Debian Server (Minimal Base System)**

You already know what to select:

### **Select ONLY**:

✔ **SSH Server**
✔ **Standard System Utilities**

### **UNSELECT**:

❌ Desktop Environment
❌ GNOME
❌ Anything graphical

This gives you a **clean, stable, secure foundation**.

---

# 🟦 **PHASE 2 — PRO Server Setup (Production-Ready)**

Below is the full plan I will guide you through **step by step** once Debian is installed.

---

# 🧱 **1. Static IP (final configuration)**

We will configure:

* `/etc/network/interfaces` **OR** NetworkManager (nmcli), depending on your choice
* Persistent static IP
* DNS fallback
* IPv4 + IPv6 (optional)
* Reverse DNS check

---

# 🔐 **2. SSH HARDENING**

Professional-grade SSH security:

* Disable root login
* Change SSH port
* Disable password login (use only SSH keys)
* Fail2ban protection for SSH
* UFW firewall rules
* Rate limits

This makes your server nearly impossible to brute-force.

---

# 🚨 **3. Fail2ban**

Protects:

* SSH
* Web login pages
* Nginx/Apache attacks
* Docker exposed ports

Install:

```bash
sudo apt install fail2ban
```

Then configure `/etc/fail2ban/jail.local` professionally.

---

# 🧱 **4. Firewall (UFW or iptables-nft)**

We will lock down the server:

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp       # or custom SSH port
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

Optional ports to open:

* Docker containers
* VPN
* Samba
* DNS

---

# 📁 **5. SAMBA PRO Configuration**

You will get professional Samba configs:

* Windows network shares
* Multiple users + permissions
* Read-only / read-write shares
* VLAN or subnet isolation
* Guest or secure shares
* Auto-mount on Windows machines

---

# 🌐 **6. DNS SERVER (optional)**

Depending on your goal, we can set up:

### ✔ BIND9 (enterprise)

### ✔ Unbound (local DNS resolver — recommended)

### ✔ Pi-hole (DNS filtering, ad blocking)

We will configure:

* Forwarders
* Zone files
* Reverse DNS
* DNSSEC support

---

# 🔒 **7. VPN Setup (WireGuard or OpenVPN)**

To access your server securely from anywhere.

We will set up:

* WireGuard (fast + simple)
* Full client configs
* QR code for mobile
* Firewall routing rules
* Port forwarding

---

# 💻 **8. IntelliJ + Java (for development server)**

We will install:

* Java 17 or 21 LTS
* IntelliJ IDEA Community or Ultimate
* Maven / Gradle
* Systemd service files for Java apps

---

# 🐳 **9. Docker + Node.js + Git**

A full DevOps stack:

```bash
sudo apt install git
sudo apt install docker.io docker-compose
sudo apt install nodejs npm
```

Plus:

* Add user to docker group
* Configure docker root directory
* Secure Docker daemon
* Reverse proxy (Nginx)
* SSL certificates for containers

---

# 💾 **10. Auto-mount Disks (Professional fstab setup)**

We will configure:

* `/etc/fstab` using UUIDs
* Auto-mount external disks
* Samba shares
* NFS if needed
* Optional LVM or RAID

---

# 🛡 **11. Backup System (Professional)**

Options:

* `rsync` automated backups
* Full daily snapshot backups
* Off-site backups (SSH or cloud)
* Email notifications
* Retention policy

Tools:

* BorgBackup
* Restic
* Duplicity

---

# 🚀 **READY TO BEGIN PHASE 2?**

To start Phase 2, I need:

1️⃣ Your server's internal static IP
(e.g., `192.168.1.50`)

2️⃣ Your network gateway
(usually `192.168.1.1` or `192.168.0.1`)

3️⃣ Your choices:

* **Nginx** or **Apache**?
* **WireGuard** or **OpenVPN**?
* **UFW** or **iptables**?

4️⃣ Do you want Docker for hosting multiple services? (**Yes/No**)

💬 Tell me these 4 things and I will begin.

---

# 🌐 **DOMAIN SETUP — rehabbot.ai**

Because your domain **rehabbot.ai** is hosted externally, you **do not** need BIND9.

You only need to:

### 🟢 1. Point your domain’s DNS records to your Debian server

### 🟢 2. Set hostname correctly

### 🟢 3. Serve your site with Nginx or Apache

### 🟢 4. (Optional) Use Unbound for local DNS caching

---

# 🟩 **External DNS Method (Recommended)**

Use your domain provider (Cloudflare, GoDaddy, etc.) to create:

## **A Records**

| Host                | Value              |
| ------------------- | ------------------ |
| `rehabbot.ai`       | **YOUR_PUBLIC_IP** |
| `www`               | **YOUR_PUBLIC_IP** |
| `server` (optional) | **YOUR_PUBLIC_IP** |

Example:

```
rehabbot.ai → 123.45.67.89
www.rehabbot.ai → 123.45.67.89
server.rehabbot.ai → 123.45.67.89
```

✔ Fast
✔ Secure
✔ No DNS server needed on Debian

---

# 🟦 **Debian Hostname (during installation)**

Use:

**Hostname:**

```
server
```

**Domain name:**

```
rehabbot.ai
```

Your FQDN becomes:

```
server.rehabbot.ai
```

---

# 🟨 **Local DNS Tool (Optional)**

Install Unbound (recommended):

```bash
sudo apt install unbound
```

---

# 🟥 **Do NOT use BIND9 unless required**

Avoid BIND9 unless you need:

* Authoritative DNS
* Internal enterprise DNS
* Multiple subdomains managed locally

---

# 🌍 **How Your Website Works**

Browser → DNS provider → Public IP → Debian server → Nginx/Apache → Your Site

Professional and standard.

---

# 🟢 **Hosting Your Website on Debian**

Your site files live in:

```
/var/www/rehabbot.ai
```

Example structure:

```
/var/www/rehabbot.ai/index.html
/var/www/rehabbot.ai/style.css
/var/www/rehabbot.ai/app.js
```

Example Nginx config:

```nginx
server {
    server_name rehabbot.ai www.rehabbot.ai;

    root /var/www/rehabbot.ai;
    index index.html index.htm;

    location / {
        try_files $uri $uri/ =404;
    }
}
```

---

# 🟧 **Router Port Forwarding**

Forward these ports to your Debian server:

* **80 → HTTP**
* **443 → HTTPS**
* **22 → SSH** *(optional)*

---

# 🟢 **Summary: Hosting on your Debian PC**

| Goal                   | Supported? | Notes                    |
| ---------------------- | ---------- | ------------------------ |
| Host website on Debian | ✔ YES      | Standard method          |
| Use your own domain    | ✔ YES      | Point A record to server |
| Professional hostname  | ✔ YES      | `server.rehabbot.ai`     |
| Nginx/Apache hosting   | ✔ YES      | Works perfectly          |
| Internet access        | ✔ YES      | Requires port forwarding |

---

# 🟣 Additional help available:

I can generate:

* ✔ Complete Nginx config
* ✔ DNS provider settings
* ✔ Port forwarding instructions
* ✔ Let’s Encrypt SSL setup
* ✔ Auto-renew configuration
* ✔ Website folder structure

Simply tell me:

1️⃣ Domain provider
2️⃣ Public IP
3️⃣ Internal Debian IP

---

# ⚠️ Installation Reminder

During Debian installation **DO NOT** select “Web Server.”

Only install:

✔ SSH Server
✔ Standard System Utilities

Then after installation:

```bash
sudo apt install nginx
```

or

```bash
sudo apt install apache2
```

---

# 🟣 Final Step

Tell me:

1️⃣ Nginx or Apache?
2️⃣ Internal server IP?
3️⃣ DNS provider?
4️⃣ Docker? Yes/No

Then I will generate your **complete, production-ready server configuration**.

---

If you'd like this exported as:

📄 PDF
📦 GitHub repo template
📘 HTML documentation

Just ask!
