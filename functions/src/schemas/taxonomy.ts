import { z } from "zod";

/**
 * Mirrors `com.dna.app.domain.taxonomy.*` on Android. Kept narrow for M3b —
 * sleeveDetail, embellishmentPlacement, length, etc. land in M5 when the full
 * generation pipeline needs them.
 */
export const GarmentType = z.enum(["SALWAR_KAMEEZ", "KURTI"]);
export const Neckline = z.enum(["V", "ROUND", "BOAT", "COLLAR", "KEYHOLE", "SQUARE", "OTHER"]);
export const SleeveStyle = z.enum([
  "FULL", "THREE_QUARTER", "HALF", "SHORT", "CAP", "SLEEVELESS", "BELL", "OTHER",
]);
export const Silhouette = z.enum([
  "ANARKALI", "STRAIGHT", "A_LINE", "FLARED", "PEPLUM", "ASYMMETRIC", "OTHER",
]);
export const Occasion = z.enum(["CASUAL", "OFFICE", "FESTIVE", "BRIDAL", "OTHER"]);
export const Embellishment = z.enum([
  "NONE", "ZARDOSI", "CHIKANKARI", "MIRROR", "SEQUINS", "EMBROIDERY_GENERIC",
  "PRINT", "OTHER",
]);

/** Hex color like "#A03F52". */
export const HexColor = z.string().regex(/^#([0-9a-fA-F]{6})$/);

export const DesignSpec = z.object({
  garmentType: GarmentType,
  neckline: Neckline,
  sleeve: SleeveStyle,
  silhouette: Silhouette,
  occasion: Occasion,
  embellishments: z.array(Embellishment).max(4),
  dominantColors: z.array(HexColor).min(1).max(3),
});

export type DesignSpec = z.infer<typeof DesignSpec>;
