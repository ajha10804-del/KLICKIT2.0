# Load Catalog Script

To load a real product catalog into KlickIt:

1. Set the admin credentials in your terminal:
   ```powershell
   $env:KLICKIT_ADMIN_EMAIL="<admin email>"
   $env:KLICKIT_ADMIN_PASSWORD="<admin password>"
   ```
2. Copy `data/catalog.sample.csv` to `data/catalog.csv` and edit it with your products.
3. Run the script: `.\scripts\load-catalog.ps1`
