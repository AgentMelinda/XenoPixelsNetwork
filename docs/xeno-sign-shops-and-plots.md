# XenoPixels sign shops and plot signs

A vanilla sign becomes a shop or a plot listing when line 1 is the right marker and the other lines follow the layout below. When it works, the sign recolors: rainbow name, white size line, gold `$` price.

**Needs MMO Econ** on the server. Without it, a click says the shop or sale needs MMO Econ.

Type a **bare number** on the price line (`2`, `12.50`, `500`). Do not write `2$` or `$2` — those fail to parse. The gold `$` is painted on top. A tag after a space is fine (`2 $`, `100 zp`).

---

## Clicks

| Action | What happens |
|---|---|
| Right-click | Buy |
| Sneak + right-click | Edit the sign (staff only) |
| Break | Only staff with the edit permission can break a finished listing |

If the sign has not turned into a listing yet, it is still a normal sign.

---

## Shop signs — `[XPSHOP]`

Sells an item. There is no chest: the server creates the item and takes the money.

### What to type (one field per line)

```
[XPSHOP]
minecraft:stone
1x
2
```

| Line | Example | Meaning |
|---|---|---|
| 1 | `[XPSHOP]` | Turns the sign into a shop. Case does not matter (`[xpshop]` works). |
| 2 | `minecraft:stone` | Item or block id. `minecraft:diamond` and `xenopixelsmod:wing_panel` both work. |
| 3 | `1x` | How many they get. Must end in `x`. Range `1x`–`9999x`. `1` alone is rejected. |
| 4 | `2` | Price in MMO Econ’s unit. |

That example is **1 stone for 2**.

A one-line form exists, but it rarely fits a vanilla sign:

```
[XPSHOP] minecraft:stone 1x 2
```

Prefer the four-line form.

### After it saves

The sign should show the item’s display name in rainbow, `1x` in white, and `$2` in gold. Right-click to buy.

### Shop permissions (LuckPerms / NeoForge)

| Node | Default | Who |
|---|---|---|
| `xenopixelsmod.shop.use` | everyone | Right-click to buy |
| `xenopixelsmod.shop.edit` | OP level 2 | Create, sneak-edit, or break the sign |
| `xenopixelsmod.admin` | OP level 2 | Grants every XenoPixels node, including both of the above |

```
/lp group default permission set xenopixelsmod.shop.use true
/lp group admin permission set xenopixelsmod.shop.edit true
```

To stop a group buying:

```
/lp group default permission set xenopixelsmod.shop.use false
```

Without `shop.edit`, a player cannot rewrite or punch out a finished shop. Without `shop.use`, they get “You cannot use shop signs.”

---

## Plot signs — `[XPLOT]`

Points at a claimed plot so a player can buy it with a click. The **charged price is the server listing** from `/plot sell`, not whatever number you later type on the sign. Put the same number on both so the gold line matches what they pay.

Plots are a 2-D footprint (X and Z only). Y is ignored. The sign’s dimension is the world it stands in — do not write a dimension name.

### 1. Claim the land

WorldEdit must be installed.

1. Select the plot with the WorldEdit wand (only X and Z are used).
2. Stand there and run `/plot claim`.
3. Check with `/plot here` (same as `/plot info`).

`/plot` and `/xenoplot` are the same command.

### 2. List it for sale

Stand in the plot you own:

```
/plot sell 500
```

Anyone standing in that plot can also run `/plot buy`. Withdraw the listing with `/plot unlist`.

### 3. Place the sign

```
[XPLOT]
10,-20
40,5
500
```

| Line | Example | Meaning |
|---|---|---|
| 1 | `[XPLOT]` | Turns the sign into a plot listing. `[xplot]` works. |
| 2 | `10,-20` | One corner as `x,z` (no spaces). |
| 3 | `40,5` | The opposite corner as `x,z`. Order does not matter. |
| 4 | `500` | Display price. Match `/plot sell`. |

The sign then shows rainbow **Plot**, a white `WxL` size, and gold `$500`.

Right-click the sign to buy (needs `xenopixelsmod.plot.use`). Money moves first; ownership only changes if payment succeeds.

### Plot permissions

| Node | Default | Who |
|---|---|---|
| `xenopixelsmod.plot.use` | everyone | Right-click a plot sign to buy |
| `xenopixelsmod.plot.edit` | OP level 2 | Create, sneak-edit, or break the plot sign |
| `xenopixelsmod.admin` | OP level 2 | Grants every XenoPixels node |

```
/lp group default permission set xenopixelsmod.plot.use true
/lp group admin permission set xenopixelsmod.plot.edit true
```

### Other plot commands

Run these while standing in the plot.

| Command | What it does |
|---|---|
| `/plot claim` | Claim your WorldEdit selection |
| `/plot unclaim` | Release a plot you own |
| `/plot here` / `/plot info` | Bounds, owner, flags |
| `/plot list` | Your plots |
| `/plot tp` | Teleport to a listed plot |
| `/plot sell <price>` | Put it on sale |
| `/plot unlist` | Take it off sale |
| `/plot buy` | Buy the plot you are standing in |
| `/plot flags` | Show build / containers / interact / pvp / entry |
| `/plot build on\|off` | Toggle that flag (owner only). Same pattern for `containers`, `interact`, `pvp`, `entry` |
| `/plot rent <price> <seconds>` | Start a lease (you are the renter) |
| `/plot unrent` | End a lease you are party to |
| `/plot lease` | Show the current lease |

YAWP is optional. Without it, plots still claim and sell; they are just not region-protected.

---

## If it does not light up

- Line 1 must be exactly `[XPSHOP]` or `[XPLOT]` (brackets included). Extra words on line 1 only work for the shop one-liner.
- Shop line 3 must look like `1x`, not `1`.
- Price must be a number (`2`), not `2$`.
- Shop line 2 must be a real registry id. A truncated `minecraft:ston` will not resolve.
- Finish the sign and close the editor so the server saves it.
- You need `shop.edit` / `plot.edit` (or OP) to **create** the listing. A player without that node cannot turn a blank sign into a shop.

## If the click fails

| Message | Meaning |
|---|---|
| This shop needs MMO Econ… | Economy mod is missing |
| You cannot afford this. | Not enough balance |
| You cannot use shop signs. / You cannot use plot signs. | Missing `shop.use` / `plot.use` |
| That plot is not for sale. | Missing `/plot sell`, or it was unlisted |
| You already own that plot. | Buyer is the seller |
| You cannot edit this shop sign. | Missing `shop.edit` (sneak-click or break) |

Shops do not take items from a chest. If the buy succeeds, the item is created and given (or dropped if the inventory is full).
