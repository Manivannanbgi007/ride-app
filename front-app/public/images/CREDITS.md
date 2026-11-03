# Image sources and licences

All photos in `categories/`, `foods/` and `delivery/` come from **Unsplash** (https://unsplash.com) and are used under the
[Unsplash License](https://unsplash.com/license): free to use, including commercially, with no permission and no
attribution required. (Attribution is still appreciated; the Unsplash page of each photo names its photographer:
open `https://unsplash.com/photos/<id>`.) The licence does not allow selling the photos on their own or building a
competing photo service from them; neither applies here.

The files were resized to 640 px wide and recompressed (JPEG, quality 78) to load fast. Two files are the same
photo under a second name, so each dish name keeps its own address.

| File                                 | Unsplash photo id                         |
| ------------------------------------ | ----------------------------------------- |
| categories/pizza.jpg                 | 1513104890138-7c749659a591                |
| categories/burgers.jpg               | 1568901346375-23c9450c58cd                |
| categories/biryani.jpg               | 1589302168068-964664d93dc0                |
| categories/chinese.jpg               | 1585032226651-759b368d7246                |
| categories/south-indian.jpg          | 1668236543090-82eba5ee5976                |
| categories/north-indian.jpg          | 1585937421612-70a008356fbe                |
| categories/desserts.jpg              | 1551024601-bec78aea704b                   |
| categories/beverages.jpg             | 1603569283847-aa295f0d016a                |
| foods/chicken-biryani.jpg            | same photo as categories/biryani.jpg      |
| foods/masala-dosa.jpg                | same photo as categories/south-indian.jpg |
| foods/egg-biryani.jpg                | 1681546898018-961e2a05c6fa                |
| foods/gulab-jamun.jpg                | 1593701461250-d7b22dfd3a77                |
| foods/hyderabadi-chicken-biryani.jpg | 1691171047462-66025ecd5efc                |
| foods/idli.jpg                       | 1589301760014-d929f3979dbc                |
| foods/malai-kulfi.jpg                | 1562812018-e84e725bc968                   |
| foods/mutton-biryani.jpg             | 1631515243349-e0cb75fb8d3a                |
| foods/paneer-biryani.jpg             | 1563379091339-03b21ab4a4f8                |
| foods/paneer-tikka.jpg               | 1567188040759-fb8a883dc6d8                |
| foods/seekh-kebab.jpg                | 1708782341231-426344114ace                |
| foods/veg-pulao.jpg                  | 1630409346824-4f0e7b080087                |
| foods/veg-thali.jpg                  | 1742281257687-092746ad6021                |
| delivery/rider.jpg                   | 1648394794449-5dbe63f6a8b5                |

The id is the part after `photo-` in the original `images.unsplash.com` address. `delivery/rider.jpg` (a rider on
a scooter with a plain delivery box, no company logos) is cropped to a 640 × 640 square around the rider, because the
delivery partner sign-in and registration pages show it in a circle.

## How the application uses them

- Admin pages and the customer area show a dish's own picture (`imageUrl` from the catalog).
- A dish without one, or whose picture cannot be loaded, shows its category's picture (`src/utils/foodImages.ts`).
- Restaurants have no photos of their own yet, so a restaurant shows the picture of its cuisine; without a matching
  cuisine, a coloured tile with its initials.
- Whatever fails to load ends in a neutral placeholder, never a broken-image icon (`FoodPhoto`).
- The sign-in and registration pages show a picture of what the account is for (`AuthShell`): a dish for customers,
  restaurant owners and admins, the delivery rider for delivery partners.
