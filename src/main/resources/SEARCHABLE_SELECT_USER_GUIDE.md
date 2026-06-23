# Searchable Asset Select - User Guide

## Overview
All asset selection dropdowns in the FAMS application now feature **professional, searchable inputs**. Instead of scrolling through long lists, users can quickly find assets by typing.

---

## Where to Find Searchable Selects

### 1. **Employee Section - Return Requests**
**Path**: Menu → Employee → Return Requests
- **Field**: "Asset" dropdown
- **What it does**: Quickly select from assigned assets when submitting return requests
- **Search by**: Asset code (e.g., "AST-2026") or name (e.g., "Dell Laptop")

### 2. **Employee Section - Maintenance Requests**
**Path**: Menu → Employee → Maintenance Requests
- **Field**: "Asset" dropdown
- **What it does**: Select assets for maintenance requests
- **Search by**: Asset code or name

### 3. **Assets - Maintenance Management**
**Path**: Menu → Assets → Maintenance Management
- **Fields**: Two searchable asset selects in modals
  - "Schedule" modal: Asset selection for preventive maintenance
  - "Corrective" modal: Asset selection for corrective maintenance
- **Search by**: Asset code or name

### 4. **Assets - Depreciation Configure**
**Path**: Menu → Assets → Depreciation → Configure Depreciation
- **Field**: "Select Asset" dropdown
- **What it does**: Choose specific assets for depreciation configuration
- **Search by**: Asset code or name

### 5. **Assets - Depreciation History**
**Path**: Menu → Assets → Depreciation → Depreciation History
- **Field**: "Select Asset" dropdown at top
- **What it does**: View complete depreciation history for selected asset
- **Search by**: Asset code or name

---

## How to Use

### Basic Usage
1. **Click** on the asset dropdown
2. **Type** part of the asset code or name
3. **See** matching results in real-time
4. **Click** to select from the filtered list

### Search Tips

| What to Search | Example | Result |
|---|---|---|
| Asset Code | `AST-2026-00001` | Finds asset with exact code |
| Partial Code | `AST-00001` | Finds all assets matching the code |
| Asset Name | `Dell` | Finds "Dell Laptop", "Dell Desktop", etc. |
| Partial Name | `Lap` | Finds "Laptop", "Lapel", etc. |
| Mixed | `Dell 2024` | Searches both code and name fields |

### Keyboard Navigation
- **↓ / ↑**: Navigate through options
- **Enter**: Select highlighted option
- **Escape**: Close dropdown
- **Tab**: Move to next field
- **Backspace**: Clear search and delete character

---

## Features

### Visual Feedback
- **Hover**: Options highlight in light red when hovering
- **Selected**: Chosen option shows in bold red
- **Search Clear**: Easy to clear and start new search
- **Placeholder**: "Search and select an asset..." indicates searchable field

### Professional Design
- Matches FAMS color scheme (Red #c0182a)
- Smooth animations
- Responsive on all devices
- Accessible with keyboard navigation

### Performance
- Searches thousands of assets instantly
- No lag or delay
- Optimized for modern browsers

---

## Example Scenarios

### Scenario 1: Finding a Specific Asset
**Goal**: Submit a return request for "Dell Monitor"

1. Go to **Return Requests** page
2. Click the **Asset** dropdown
3. Type `"Dell"` 
4. See filtered results showing:
   - "AST-2026-00456 - Dell Monitor"
   - "AST-2026-00789 - Dell Laptop"
5. Click **"Dell Monitor"** to select
6. Continue with return request

### Scenario 2: Asset Code Search
**Goal**: Find asset "AST-2026-00123" for maintenance

1. Go to **Maintenance Management** → **Log Corrective**
2. Click **Asset** dropdown
3. Type `"00123"` (partial code)
4. See matching asset appear
5. Click to select
6. Fill in maintenance details

### Scenario 3: Maintenance Schedule
**Goal**: Create preventive maintenance for all laptops

1. Go to **Maintenance Management** → **New Schedule**
2. Click **Asset** dropdown
3. Type `"Laptop"` to see all laptops
4. Select one laptop OR
5. Use **Category** field to select "IT Equipment" category
6. Define schedule details

---

## Benefits

### For Users
- **Faster**: Find assets in seconds, not minutes
- **Easier**: No more scrolling through massive lists
- **Flexible**: Search by code, name, or partial matches
- **Cleaner**: Modern, professional appearance

### For the System
- **Scalable**: Works with any number of assets
- **Responsive**: No slowdown with large datasets
- **Compatible**: Works on all devices (desktop, tablet, mobile)
- **Accessible**: Supports keyboard navigation

---

## Troubleshooting

### The dropdown won't search
- Ensure you've clicked in the search field
- Try typing part of the asset name or code
- Clear any previous searches with Backspace

### Can't find an asset
- Check the exact asset code or name
- Try searching by a different field (code vs. name)
- The asset may not exist in the system

### Dropdown is slow
- This is highly unlikely - Tom-Select is optimized
- Try refreshing the page if performance is poor
- Report any issues to IT support

---

## FAQ

**Q: Can I still type the full asset code?**
A: Yes! Type the complete code or just the parts you remember.

**Q: Does it search both code and name?**
A: Yes, it searches both fields simultaneously.

**Q: What if my asset isn't showing up?**
A: The asset may be archived, deleted, or you may need to refresh the page.

**Q: Can I select multiple assets?**
A: The current implementation selects one asset per form. Multi-select may be added in future updates.

**Q: Is this available on mobile?**
A: Yes! The searchable select works perfectly on mobile devices.

---

## Technical Notes

- **Library**: Tom-Select v2.2.2 (lightweight, ~30KB)
- **Dependency**: None (no jQuery required)
- **CDN**: Loaded from jsDelivr CDN
- **Styling**: Custom CSS matching FAMS design system

---

## Support

For issues with searchable selects, please:
1. Clear browser cache and refresh
2. Try a different browser
3. Contact IT support with:
   - What page you were on
   - What asset you were searching for
   - Any error messages seen

---

**Last Updated**: June 22, 2026
**Version**: 1.0

