# CamCheck Mobile App Design System

This document outlines the design system for the CamCheck mobile application, ensuring consistency with the existing brand while adapting to mobile platform conventions.

## Brand Identity

The CamCheck mobile app maintains the core brand identity while adapting it for mobile use:

- **Brand Values**: Security, Reliability, Simplicity
- **Brand Voice**: Professional, Clear, Trustworthy
- **Visual Style**: Modern, Minimal, Focused

## Color Palette

### Primary Colors

| Color | Hex | Usage |
|-------|-----|-------|
| Brand Black | `#000000` | Primary background |
| Brand White | `#FFFFFF` | Text and icons |
| Brand Gray | `#333333` | Secondary background |
| Light Gray | `#999999` | Disabled elements |

### Accent Colors

| Color | Hex | Usage |
|-------|-----|-------|
| Action Blue | `#0066CC` | Interactive elements, buttons |
| Success Green | `#33CC66` | Success states, active indicators |
| Warning Yellow | `#FFCC00` | Warnings, notifications |
| Error Red | `#CC3300` | Errors, critical alerts |

### Dark Mode / Light Mode

The app supports both dark and light modes, with automatic switching based on device settings:

**Dark Mode (Default)**
- Background: Brand Black (`#000000`)
- Text: Brand White (`#FFFFFF`)
- Surfaces: Brand Gray (`#333333`)

**Light Mode**
- Background: Off-White (`#F5F5F5`)
- Text: Dark Gray (`#212121`)
- Surfaces: White (`#FFFFFF`)

## Typography

### Font Family

The app uses a system font stack for optimal performance and platform consistency:

```dart
fontFamily: 'SF Pro Text', // iOS
fontFamily: 'Roboto', // Android
```

### Type Scale

| Style | Size | Weight | Usage |
|-------|------|--------|-------|
| headingLarge | 24sp | Bold | Screen titles |
| headingMedium | 20sp | Bold | Section headers |
| headingSmall | 18sp | Bold | Card titles |
| bodyLarge | 16sp | Regular | Primary content |
| bodyMedium | 14sp | Regular | Secondary content |
| bodySmall | 12sp | Regular | Supporting text |
| caption | 11sp | Regular | Labels, timestamps |
| button | 16sp | Medium | Button text |

### Text Styles

```dart
// Sample text style implementation
final TextStyle headingLarge = TextStyle(
  fontSize: 24,
  fontWeight: FontWeight.bold,
  letterSpacing: 0.5,
);
```

## Spacing

A consistent 4-point spacing system:

| Name | Size (dp) | Usage |
|------|-----------|-------|
| spacing2xs | 4 | Minimal spacing |
| spacingXs | 8 | Element separation |
| spacingS | 16 | Internal padding |
| spacingM | 24 | Content block padding |
| spacingL | 32 | Section separation |
| spacingXl | 48 | Screen padding top/bottom |
| spacing2xl | 64 | Feature separation |

## UI Components

### Buttons

**Primary Button**
- Background: Action Blue
- Text: White
- Height: 48dp
- Corner Radius: 4dp
- Padding: 16dp horizontal

**Secondary Button**
- Background: Transparent
- Border: 1dp White
- Text: White
- Height: 48dp
- Corner Radius: 4dp
- Padding: 16dp horizontal

**Text Button**
- Background: Transparent
- Text: White
- Padding: 8dp

**Icon Button**
- Size: 48dp x 48dp
- Icon Size: 24dp x 24dp

### Cards

**Standard Card**
- Background: Brand Gray
- Border: None
- Corner Radius: 4dp
- Padding: 16dp
- Elevation: None (flat design)

**Interactive Card**
- Same as Standard Card
- State changes on tap (subtle opacity change)

### Input Fields

**Text Field**
- Background: Brand Gray
- Border: 1dp White
- Text: White
- Height: 48dp
- Corner Radius: 4dp
- Padding: 16dp horizontal

**Selection Controls**
- Switches: Custom implementation with brand colors
- Checkboxes: Custom implementation with brand colors
- Radio Buttons: Custom implementation with brand colors

### Status Indicators

**Status Badge**
- Size: Auto
- Padding: 4dp vertical, 8dp horizontal
- Corner Radius: 4dp
- Text: White
- Background: Varies by status

**Status Indicator Dot**
- Size: 8dp
- Shape: Circle
- Color: Varies by status

## Iconography

### Icon Style

- Line weight: 1.5dp
- Corner radius: 1dp
- Size: 24dp x 24dp

### Icon Set

The app uses a combination of:
- Material Design icons for standard UI elements
- Custom icons for app-specific features

### UI Icons

| Icon | Name | Usage |
|------|------|-------|
| 📷 | camera | Camera access |
| 🔒 | lock | Security features |
| 🔔 | notification | Alerts |
| ⚙️ | settings | App settings |
| 👥 | users | Session participants |

## Motion

### Transitions

- Page transitions: Slide from right
- Dialog entrance: Fade in, slight scale up
- Dialog exit: Fade out
- Element transitions: 300ms ease-in-out

### Feedback Animations

- Button press: 100ms scale down to 0.95
- Success animation: 500ms checkmark draw
- Error animation: 300ms shake

## Layouts

### Screen Templates

**Standard Screen**
- Status bar: Transparent
- App bar: 56dp height
- Content: Scrolling with 16dp padding
- Bottom safe area padding: 16dp

**Camera Screen**
- Fullscreen with minimal UI
- Controls overlay with 50% opacity background
- Bottom control panel: 80dp height

**Settings Screen**
- Grouped list items
- Section headers
- 56dp item height
- 16dp horizontal padding

### Grid System

- 8dp baseline grid
- 16dp gutter
- Responsive column system:
  - Phone: 4-column grid
  - Tablet: 8-column grid

## Accessibility

### Contrast

All text and interactive elements meet WCAG AA standards:
- Regular text: 4.5:1 contrast ratio
- Large text: 3:1 contrast ratio

### Touch Targets

- Minimum interactive element size: 48dp x 48dp
- Minimum spacing between elements: 8dp

### Text Scaling

All text supports dynamic type scaling for accessibility

## Implementation Guidelines

### ThemeData Implementation

```dart
ThemeData darkTheme = ThemeData(
  brightness: Brightness.dark,
  primaryColor: Color(0xFF000000),
  scaffoldBackgroundColor: Color(0xFF000000),
  colorScheme: ColorScheme.dark(
    primary: Color(0xFFFFFFFF),
    secondary: Color(0xFF0066CC),
    surface: Color(0xFF333333),
    background: Color(0xFF000000),
    error: Color(0xFFCC3300),
  ),
  textTheme: TextTheme(
    // Define text styles here
  ),
  // Other theme properties
);
```

### Component Library

All UI components should be created as reusable widgets in the `lib/widgets` directory, following these guidelines:
- Consistent naming conventions
- Well-documented parameters
- Support for dark/light modes
- Accessibility compliance

## Asset Guidelines

### Image Assets

- Resolution support: 1x, 2x, 3x
- Format: PNG for icons, SVG for simple graphics, JPEG for photos
- Maximum size: 500KB per image

### Animation Assets

- Format: Lottie JSON for complex animations
- Maximum size: 100KB per animation
- Duration: < 3 seconds for most UI animations 