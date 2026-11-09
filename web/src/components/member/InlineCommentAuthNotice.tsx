"use client";

import { createContext, useContext } from "react";

// The page owns this notice because confirmed expiry unmounts private activity.
// No member data or draft is passed through this notification.
export const InlineCommentAuthNotice = createContext<(() => void) | null>(null);
export const useInlineCommentAuthNotice = () => useContext(InlineCommentAuthNotice);
