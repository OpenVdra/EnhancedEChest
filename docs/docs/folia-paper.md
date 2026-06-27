# Paper vs Folia

EnhancedEchest runs on Paper, Folia, and Paper forks such as Purpur. Almost everything behaves identically on every platform. There is one difference worth knowing about, and it only comes up when the **same chest** is opened on two screens at once.

## The one difference: opening the same chest twice at once

A single ender chest can be open on more than one screen at the same time. The common case is an admin running `/ee view` on a chest while its owner already has that chest open.

| Situation | Paper | Folia |
|-----------|-------|-------|
| Two people open the **same** chest at once | Both can view and edit it together, kept in sync | Only the first viewer is allowed in; the second is told the chest is **in use** |
| Opening **different** chests | Works for everyone, always | Works for everyone, always |
| One player, one chest at a time | Normal | Normal |

In short: on Paper, an admin and the owner (or two admins) can have the very same chest open side by side. On Folia, a chest is limited to one viewer at a time, so the second person to open it is asked to try again in a moment.

The reason is simply that Folia spreads different parts of the world across separate threads, so the plugin keeps each individual chest to a single live viewer to stay safe. Nothing is lost, and no items can ever be duplicated on either platform.

## What an admin notices

When you run `/ee view` (or use the **Clear chest** button) on a chest the owner currently has open:

- **Paper**: the chest opens and you can watch or edit it live alongside the owner.
- **Folia**: the action is declined with an "in use" message until the owner closes the chest. Run the command again once it is free, or act while the owner is offline.

This is the only player-visible difference. If your server is on Paper, you never see the "in use" message at all.

## Everything else is identical

Storage, multiple chests per player, custom names and icons, permission-granted chests, expiring and temporary chests, migration from other plugins, account transfers, and every command work exactly the same on Paper and Folia. You can move a world between the two without changing anything in EnhancedEchest.
