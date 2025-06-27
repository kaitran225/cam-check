#!/bin/bash
# CamCheck Deployment Script

# Exit on error
set -e

# Check if running as root
if [ "$EUID" -ne 0 ]; then
  echo "Please run as root"
  exit 1
fi

# Configuration variables
DOMAIN="yourdomain.com"
APP_DIR="/opt/camcheck"
SERVICE_NAME="camcheck"
APP_USER="camcheck"
APP_GROUP="camcheck"
JAR_FILE="cam-check-0.0.1-SNAPSHOT.jar"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Starting CamCheck deployment...${NC}"

# Update system
echo -e "${YELLOW}Updating system packages...${NC}"
apt update && apt upgrade -y

# Install required packages
echo -e "${YELLOW}Installing required packages...${NC}"
apt install -y openjdk-17-jdk nginx certbot python3-certbot-nginx

# Create user and group if they don't exist
echo -e "${YELLOW}Setting up user and group...${NC}"
id -u $APP_USER &>/dev/null || useradd -r -s /bin/false $APP_USER
getent group $APP_GROUP &>/dev/null || groupadd -r $APP_GROUP

# Create application directory
echo -e "${YELLOW}Creating application directory...${NC}"
mkdir -p $APP_DIR
mkdir -p $APP_DIR/recordings

# Copy files
echo -e "${YELLOW}Copying application files...${NC}"
cp ../$JAR_FILE $APP_DIR/
cp ../deploy/camcheck.service /etc/systemd/system/
cp ../deploy/nginx.conf /etc/nginx/sites-available/$DOMAIN

# Update domain in nginx config
echo -e "${YELLOW}Configuring Nginx...${NC}"
sed -i "s/yourdomain.com/$DOMAIN/g" /etc/nginx/sites-available/$DOMAIN

# Enable site
ln -sf /etc/nginx/sites-available/$DOMAIN /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default

# Set permissions
echo -e "${YELLOW}Setting permissions...${NC}"
chown -R $APP_USER:$APP_GROUP $APP_DIR
chmod +x $APP_DIR/$JAR_FILE

# Reload systemd
echo -e "${YELLOW}Configuring systemd service...${NC}"
systemctl daemon-reload
systemctl enable $SERVICE_NAME

# Configure SSL with Certbot
echo -e "${YELLOW}Setting up SSL with Let's Encrypt...${NC}"
certbot --nginx -d $DOMAIN --non-interactive --agree-tos --email admin@$DOMAIN

# Restart Nginx
echo -e "${YELLOW}Restarting Nginx...${NC}"
systemctl restart nginx

# Start the application
echo -e "${YELLOW}Starting CamCheck application...${NC}"
systemctl start $SERVICE_NAME

# Show status
echo -e "${GREEN}Deployment completed!${NC}"
echo -e "CamCheck is now running at https://$DOMAIN"
echo -e "Service status:"
systemctl status $SERVICE_NAME --no-pager

echo -e "\n${YELLOW}Important:${NC}"
echo -e "1. Make sure your domain DNS points to this server's IP address"
echo -e "2. Change the default credentials in application.yml"
echo -e "3. Configure your firewall to allow ports 80 and 443"
echo -e "4. Set up regular backups of your recordings" 