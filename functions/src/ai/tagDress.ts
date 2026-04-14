import { onCall, HttpsError } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import * as admin from "firebase-admin";
import { VertexAI } from "@google-cloud/vertexai";
import { z } from "zod";
import { DesignSpec } from "../schemas/taxonomy";

/**
 * M3b: auto-tag an uploaded dress.
 *
 * Input: { dressId: string }
 * Flow:
 *   1. Auth + App Check gates.
 *   2. Load `dresses/{dressId}` — caller must own it.
 *   3. Download the display-tier JPEG from Storage.
 *   4. Call Gemini 2.5 Flash with structured-output constrained to DesignSpec.
 *   5. Validate with zod, write back to `dresses/{dressId}.designSpec`.
 *
 * Retrieval (references / embeddings) arrives in M5/M7.
 */

const Input = z.object({ dressId: z.string().min(1) });

// JSON schema mirror of DesignSpec for Gemini's responseSchema.
// Vertex accepts a subset of OpenAPI 3.0 — keep it flat and explicit.
const responseSchema = {
  type: "object",
  properties: {
    garmentType: { type: "string", enum: ["SALWAR_KAMEEZ", "KURTI"] },
    neckline: {
      type: "string",
      enum: ["V", "ROUND", "BOAT", "COLLAR", "KEYHOLE", "SQUARE", "OTHER"],
    },
    sleeve: {
      type: "string",
      enum: ["FULL", "THREE_QUARTER", "HALF", "SHORT", "CAP", "SLEEVELESS", "BELL", "OTHER"],
    },
    silhouette: {
      type: "string",
      enum: ["ANARKALI", "STRAIGHT", "A_LINE", "FLARED", "PEPLUM", "ASYMMETRIC", "OTHER"],
    },
    occasion: {
      type: "string",
      enum: ["CASUAL", "OFFICE", "FESTIVE", "BRIDAL", "OTHER"],
    },
    embellishments: {
      type: "array",
      maxItems: 4,
      items: {
        type: "string",
        enum: [
          "NONE", "ZARDOSI", "CHIKANKARI", "MIRROR", "SEQUINS",
          "EMBROIDERY_GENERIC", "PRINT", "OTHER",
        ],
      },
    },
    dominantColors: {
      type: "array",
      minItems: 1,
      maxItems: 3,
      items: { type: "string" },
    },
  },
  required: [
    "garmentType", "neckline", "sleeve", "silhouette",
    "occasion", "embellishments", "dominantColors",
  ],
};

const PROMPT = `You are a fashion taxonomy classifier for South Asian womenswear (salwar kameez and kurtis).
Inspect the garment in the photo and return a DesignSpec as JSON matching the provided schema.
Rules:
- Pick the SINGLE best enum value for each categorical field. Use OTHER only if nothing fits.
- embellishments: list up to 4 distinct techniques visible; use ["NONE"] if the garment is plain.
- dominantColors: 1-3 hex strings like "#A03F52" for the primary fabric colors (ignore skin/background).
- Do not invent details that are occluded or unclear — prefer OTHER.`;

export const tagDress = onCall(
  {
    region: "asia-south1",
    enforceAppCheck: true,
    memory: "512MiB",
    timeoutSeconds: 60,
  },
  async (req) => {
    const uid = req.auth?.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Sign in required.");

    const parsed = Input.safeParse(req.data);
    if (!parsed.success) {
      throw new HttpsError("invalid-argument", parsed.error.message);
    }
    const { dressId } = parsed.data;

    const db = admin.firestore();
    const snap = await db.doc(`dresses/${dressId}`).get();
    if (!snap.exists) throw new HttpsError("not-found", "Dress not found.");
    const data = snap.data()!;
    if (data.ownerUid !== uid) {
      throw new HttpsError("permission-denied", "Not your dress.");
    }

    // Pull the display tier from Storage. Path mirrors StorageSource on Android.
    const bucket = admin.storage().bucket();
    const objectPath = `users/${uid}/dresses/${dressId}/display.jpg`;
    const [buffer] = await bucket.file(objectPath).download();
    const imageB64 = buffer.toString("base64");

    const project = process.env.GCLOUD_PROJECT || process.env.GCP_PROJECT;
    if (!project) throw new HttpsError("internal", "Project id unresolved.");

    const vertex = new VertexAI({ project, location: "asia-south1" });
    const model = vertex.getGenerativeModel({
      model: "gemini-2.5-flash",
      generationConfig: {
        responseMimeType: "application/json",
        responseSchema: responseSchema as never,
        temperature: 0.2,
      },
    });

    const result = await model.generateContent({
      contents: [
        {
          role: "user",
          parts: [
            { text: PROMPT },
            { inlineData: { mimeType: "image/jpeg", data: imageB64 } },
          ],
        },
      ],
    });

    const text = result.response.candidates?.[0]?.content?.parts?.[0]?.text;
    if (!text) {
      logger.error("tagDress: empty model response", { dressId });
      throw new HttpsError("internal", "Model returned empty response.");
    }

    let json: unknown;
    try {
      json = JSON.parse(text);
    } catch (e) {
      logger.error("tagDress: non-JSON response", { dressId, text });
      throw new HttpsError("internal", "Model returned non-JSON.");
    }

    const spec = DesignSpec.safeParse(json);
    if (!spec.success) {
      logger.error("tagDress: spec validation failed", { dressId, issues: spec.error.issues });
      throw new HttpsError("internal", "Model response failed validation.");
    }

    await snap.ref.update({
      designSpec: spec.data,
      taggedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    return { dressId, designSpec: spec.data };
  },
);
