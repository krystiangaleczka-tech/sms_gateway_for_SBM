# SMS Gateway - Cloudflare Tunnel Configuration Guide

## Overview

This guide provides detailed instructions for configuring Cloudflare Tunnel to provide secure external access to your SMS Gateway without opening ports in your firewall.

## Prerequisites

- Cloudflare account (free tier is sufficient)
- Domain name managed by Cloudflare
- Android device with SMS Gateway installed
- Computer with internet access for configuration

## What is Cloudflare Tunnel?

Cloudflare Tunnel is a secure, outbound-only connection between your server and Cloudflare's network. It allows you to expose local services to the internet without:

- Opening firewall ports
- Configuring port forwarding
- Managing SSL certificates
- Revealing your public IP address

## Architecture

```
┌─────────────────┐    HTTPS/WSS    ┌─────────────────┐    Argo Tunnel    ┌─────────────────┐
│   Client App    │ ◄──────────────► │  Cloudflare CDN │ ◄────────────────► │  SMS Gateway    │
│ (Web/Mobile)    │                  │ (yourdomain.com)│                  │  (Local Device) │
└─────────────────┘                  └─────────────────┘                  └─────────────────┘
```

## Step 1: Cloudflare Account Setup

### 1.1 Create Cloudflare Account

1. Go to [https://dash.cloudflare.com/sign-up](https://dash.cloudflare.com/sign-up)
2. Create a free account
3. Verify your email address

### 1.2 Add Your Domain

1. In Cloudflare dashboard, click "Add a site"
2. Enter your domain name (e.g., `yourdomain.com`)
3. Select the Free plan
4. Follow the DNS setup instructions

### 1.3 Update DNS Records

Ensure your domain has at least:
- An A record for your domain (can point to any IP, won't be used for tunnel)
- Optional: A subdomain for the tunnel (e.g., `sms.yourdomain.com`)

## Step 2: Install Cloudflared

### 2.1 Linux/macOS

```bash
# Add Cloudflare GPG key
curl -L https://pkg.cloudflare.com/pubkey.gpg | sudo gpg --dearmor -o /usr/share/keyrings/cloudflare-archive-keyring.gpg

# Add Cloudflare repository
echo 'deb [signed-by=/usr/share/keyrings/cloudflare-archive-keyring.gpg] https://pkg.cloudflare.com/ cloudflare-main' | sudo tee /etc/apt/sources.list.d/cloudflare.list

# Update package list and install cloudflared
sudo apt-get update && sudo apt-get install cloudflared
```

### 2.2 Windows

1. Download the latest release from [GitHub](https://github.com/cloudflare/cloudflared/releases/latest)
2. Extract the ZIP file
3. Move `cloudflared.exe` to a location in your PATH (e.g., `C:\Windows\System32`)

### 2.3 Android (Termux)

```bash
# Install Termux from F-Droid (not Google Play)
pkg update && pkg upgrade
pkg install curl

# Download cloudflared for Android ARM
curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64 -o cloudflared
chmod +x cloudflared
mv cloudflared $PREFIX/bin/
```

### 2.4 Verify Installation

```bash
cloudflared --version
```

## Step 3: Authenticate Cloudflared

### 3.1 Login to Cloudflare

```bash
cloudflared tunnel login
```

This will:
1. Open a browser window
2. Ask you to log in to Cloudflare
3. Request permission to access your account
4. Generate a credentials file

### 3.2 Credentials File Location

- Linux/macOS: `~/.cloudflared/<tunnel-id>.json`
- Windows: `%USERPROFILE%\.cloudflared\<tunnel-id>.json`
- Android/Termux: `~/.cloudflared/<tunnel-id>.json`

## Step 4: Create a Tunnel

### 4.1 Create Tunnel

```bash
cloudflared tunnel create sms-gateway
```

This will output:
```
Tunnel credentials written to /path/to/.cloudflared/<tunnel-id>.json
Tunnel ID: <tunnel-id>
```

Note the tunnel ID for later use.

### 4.2 Create Configuration File

Create a configuration file at `~/.cloudflared/config.yml`:

```yaml
# ~/.cloudflared/config.yml
tunnel: <tunnel-id>  # Replace with your tunnel ID
credentials-file: ~/.cloudflared/<tunnel-id>.json

# Ingress rules - order matters!
ingress:
  # SMS Gateway API
  - hostname: sms.yourdomain.com
    service: http://localhost:8080
    originRequest:
      noTLSVerify: false
      
  # Optional: Web UI
  - hostname: sms-ui.yourdomain.com
    service: http://localhost:8080
    originRequest:
      noTLSVerify: false
      
  # Fallback for all other requests
  - service: http_status:404
```

### 4.3 Create DNS Records

```bash
cloudflared tunnel route dns sms-gateway sms.yourdomain.com
```

This creates a CNAME record pointing to your tunnel.

## Step 5: Configure SMS Gateway

### 5.1 Enable External Access

In SMS Gateway app:

1. Go to Settings → Security
2. Enable "External Access"
3. Select "Cloudflare Tunnel" as access method
4. Configure:
   - Subdomain: `sms`
   - Domain: `yourdomain.com`
   - Local Port: `8080` (default)

### 5.2 Configure Security

1. Go to Settings → Security → API Tokens
2. Create a new API token with appropriate permissions
3. Note the token for external access

## Step 6: Start the Tunnel

### 6.1 Start Tunnel Manually

```bash
cloudflared tunnel run sms-gateway
```

### 6.2 Start Tunnel as Service (Linux)

Create a systemd service file:

```bash
sudo nano /etc/systemd/system/cloudflared-sms.service
```

Content:

```ini
[Unit]
Description=Cloudflare Tunnel for SMS Gateway
After=network.target

[Service]
Type=simple
User=your-username
ExecStart=/usr/bin/cloudflared tunnel run sms-gateway
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

Enable and start the service:

```bash
sudo systemctl enable cloudflared-sms
sudo systemctl start cloudflared-sms
```

Check status:

```bash
sudo systemctl status cloudflared-sms
```

### 6.3 Start Tunnel as Service (Android)

Create a startup script in Termux:

```bash
# Create startup directory
mkdir -p ~/.termux/boot

# Create startup script
cat > ~/.termux/boot/start-tunnel.sh << 'EOF'
#!/data/data/com.termux/files/usr/bin/sh

# Start cloudflared tunnel in background
cloudflared tunnel run sms-gateway &
EOF

# Make script executable
chmod +x ~/.termux/boot/start-tunnel.sh
```

Note: This requires Termux:Boot plugin for automatic startup.

## Step 7: Verify Connection

### 7.1 Test API Access

```bash
# Test health endpoint
curl -H "Authorization: Bearer YOUR_TOKEN" https://sms.yourdomain.com/api/health

# Test SMS endpoint
curl -H "Authorization: Bearer YOUR_TOKEN" https://sms.yourdomain.com/api/sms
```

### 7.2 Test Web UI

Open a browser and navigate to:
```
https://sms.yourdomain.com
```

You should see the SMS Gateway web interface.

## Step 8: Advanced Configuration

### 8.1 Custom Subdomains

Update your `config.yml` to include multiple subdomains:

```yaml
ingress:
  # API endpoint
  - hostname: api.sms.yourdomain.com
    service: http://localhost:8080
    
  # Web UI
  - hostname: app.sms.yourdomain.com
    service: http://localhost:8080
    
  # Admin interface
  - hostname: admin.sms.yourdomain.com
    service: http://localhost:8080
    
  # Fallback
  - service: http_status:404
```

Create DNS records for each subdomain:

```bash
cloudflared tunnel route dns sms-gateway api.sms.yourdomain.com
cloudflared tunnel route dns sms-gateway app.sms.yourdomain.com
cloudflared tunnel route dns sms-gateway admin.sms.yourdomain.com
```

### 8.2 Access Policies

Configure Zero Trust access policies in Cloudflare:

1. Go to Cloudflare Dashboard → Zero Trust → Access
2. Create an Application:
   - Application type: Self-hosted
   - Name: SMS Gateway
   - Domain: `sms.yourdomain.com`
3. Configure access policies:
   - Email domains (e.g., `@yourcompany.com`)
   - Specific email addresses
   - OTP authentication
   - Hardware key authentication

### 8.3 IP Restrictions

Add IP restrictions to your tunnel configuration:

```yaml
ingress:
  - hostname: sms.yourdomain.com
    service: http://localhost:8080
    originRequest:
      ipRules:
        - allow: ["192.168.1.0/24", "10.0.0.0/8"]
        - deny: ["0.0.0.0/0"]
```

## Step 9: Monitoring and Troubleshooting

### 9.1 Monitor Tunnel Status

```bash
# Check tunnel info
cloudflared tunnel info sms-gateway

# View active connections
cloudflared tunnel list
```

### 9.2 View Logs

```bash
# View real-time logs
cloudflared tunnel run sms-gateway --loglevel debug

# View systemd logs (Linux)
journalctl -u cloudflared-sms -f
```

### 9.3 Common Issues

#### Tunnel won't start

1. Check if port 8080 is available:
   ```bash
   netstat -tlnp | grep 8080
   ```

2. Verify configuration file:
   ```bash
   cloudflared tunnel ingress validate
   ```

3. Check credentials file permissions:
   ```bash
   ls -la ~/.cloudflared/
   ```

#### DNS not resolving

1. Verify DNS records in Cloudflare dashboard
2. Check propagation:
   ```bash
   dig sms.yourdomain.com
   ```

3. Clear local DNS cache:
   ```bash
   # Linux
   sudo systemd-resolve --flush-caches
   
   # macOS
   sudo dscacheutil -flushcache
   
   # Windows
   ipconfig /flushdns
   ```

#### Connection timeouts

1. Check if SMS Gateway is running:
   ```bash
   curl http://localhost:8080/api/health
   ```

2. Verify firewall settings
3. Check Cloudflare status page

## Step 10: Security Best Practices

### 10.1 Token Security

1. Use strong, unique API tokens
2. Set appropriate expiration dates
3. Regularly rotate tokens
4. Limit token permissions to minimum required

### 10.2 Access Control

1. Implement Zero Trust access policies
2. Use multi-factor authentication
3. Restrict access by IP address when possible
4. Regularly review access logs

### 10.3 Monitoring

1. Set up alerts for suspicious activity
2. Monitor tunnel connection status
3. Regularly review Cloudflare analytics
4. Implement rate limiting

### 10.4 Backup and Recovery

1. Backup tunnel configuration:
   ```bash
   cp ~/.cloudflared/config.yml ~/.cloudflared/config.yml.backup
   cp ~/.cloudflared/<tunnel-id>.json ~/.cloudflared/<tunnel-id>.json.backup
   ```

2. Document recovery procedures
3. Test disaster recovery scenarios

## Step 11: Performance Optimization

### 11.1 Caching

Enable Cloudflare caching for static assets:

1. Go to Cloudflare Dashboard → Caching
2. Configure caching rules for static content
3. Set appropriate cache TTL values

### 11.2 Compression

Enable compression in Cloudflare:

1. Go to Cloudflare Dashboard → Speed → Optimization
2. Enable Auto Minify for HTML, CSS, JavaScript
3. Enable Brotli compression

### 11.3 Argo Smart Routing

For improved performance, consider upgrading to a paid plan with Argo Smart Routing.

## Conclusion

You now have a secure, external access to your SMS Gateway using Cloudflare Tunnel. This setup provides:

- 🔒 Secure HTTPS connections
- 🛡️ DDoS protection
- 🚫 No open firewall ports
- 📱 Access from anywhere
- 🔧 Easy management

For ongoing maintenance:

- Regularly update cloudflared
- Monitor tunnel status
- Review security logs
- Update access policies as needed

## Additional Resources

- [Cloudflare Tunnel Documentation](https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/install-and-setup/tunnel-guide/)
- [Zero Trust Documentation](https://developers.cloudflare.com/cloudflare-one/)
- [SMS Gateway Security Guide](./SECURITY_GUIDE.md)
- [Security API Documentation](./SECURITY_API.md)