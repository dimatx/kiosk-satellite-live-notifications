// SPDX-License-Identifier: Apache-2.0
package com.shymoose.livenotifications;

import java.util.List;

/** Selection uses configured row order, not snapshot arrival order. */
final class Rotation {
    private int current = -1;
    private int previousCount;
    private long deadline;

    NotificationModel.Item select(List<NotificationModel.Item> items, long now, int dwellMs) {
        if (items.isEmpty()) {
            reset();
            return null;
        }
        int position = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id == current) position = i;
        }
        if (position == -1) {
            position = 0;
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).id > current) {
                    position = i;
                    break;
                }
            }
            deadline = now + dwellMs;
        } else if (items.size() > 1) {
            if (previousCount <= 1) {
                deadline = now + dwellMs;
            } else if (now >= deadline) {
                position = (position + 1) % items.size();
                deadline = now + dwellMs;
            }
        }
        current = items.get(position).id;
        previousCount = items.size();
        return items.get(position);
    }

    long nextDeadline() {
        return previousCount > 1 ? deadline : 0;
    }

    void reset() {
        current = -1;
        previousCount = 0;
        deadline = 0;
    }
}
