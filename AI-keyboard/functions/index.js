/**
 * Import function triggers from their respective submodules:
 *
 * const {onCall} = require("firebase-functions/v2/https");
 * const {onDocumentWritten} = require("firebase-functions/v2/firestore");
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

const {setGlobalOptions} = require("firebase-functions");
const {onRequest} = require("firebase-functions/v2/https");
const {onDocumentCreated} = require("firebase-functions/v2/firestore");
const {defineSecret} = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");
const axios = require("axios");

// Define the secret for OpenAI API key
const openaiApiKeySecret = defineSecret("OPENAI_API_KEY");

admin.initializeApp();

// For cost control, you can set the maximum number of containers that can be
// running at the same time. This helps mitigate the impact of unexpected
// traffic spikes by instead downgrading performance. This limit is a
// per-function limit. You can override the limit for each function using the
// `maxInstances` option in the function's options, e.g.
// `onRequest({ maxInstances: 5 }, (req, res) => { ... })`.
// NOTE: setGlobalOptions does not apply to functions using the v1 API. V1
// functions should each use functions.runWith({ maxInstances: 10 }) instead.
// In the v1 API, each function can only serve one request per container, so
// this will be the maximum concurrent request count.
setGlobalOptions({ maxInstances: 10 });

/**
 * Cloud Function to send FCM notifications when a new notification is created
 * in the broadcastNotifications collection
 */
exports.sendBroadcastNotification = onDocumentCreated(
  {
    document: "broadcastNotifications/{notificationId}",
    maxInstances: 10,
  },
  async (event) => {
    const notificationData = event.data.data();
    const notificationId = event.params.notificationId;

    logger.info(`Processing notification: ${notificationId}`, {
      title: notificationData.title,
      target: notificationData.target,
    });

    try {
      const title = notificationData.title || "New Notification";
      const body = notificationData.body || "";
      const link = notificationData.link || null;
      const target = notificationData.target || "all_devices";
      const tokens = notificationData.tokens || [];

      const message = {
        notification: {
          title: title,
          body: body,
        },
        data: {
          click_action: "FLUTTER_NOTIFICATION_CLICK",
          ...(link && { link: link }),
        },
        android: {
          priority: "high",
          notification: {
            sound: "default",
            channelId: "default",
          },
        },
        apns: {
          payload: {
            aps: {
              sound: "default",
            },
          },
        },
      };

      let successCount = 0;
      let failureCount = 0;

      if (target === "all_devices" || target === "specific_devices") {
        // Send to specific device tokens
        if (tokens.length === 0) {
          // Fetch all device tokens from Firestore
          const devicesSnapshot = await admin
            .firestore()
            .collection("devices")
            .get();

          const allTokens = devicesSnapshot.docs
            .map((doc) => doc.data().token)
            .filter((token) => token != null && token.length > 0);

          if (allTokens.length === 0) {
            logger.warn("No device tokens found");
            await admin
              .firestore()
              .collection("broadcastNotifications")
              .doc(notificationId)
              .update({
                status: "failed",
                error: "No device tokens found",
              });
            return;
          }

          // Send in batches of 500 (FCM limit)
          const batchSize = 500;
          for (let i = 0; i < allTokens.length; i += batchSize) {
            const batch = allTokens.slice(i, i + batchSize);
            try {
              const response = await admin.messaging().sendEachForMulticast({
                ...message,
                tokens: batch,
              });

              successCount += response.successCount;
              failureCount += response.failureCount;

              // Remove invalid tokens
              if (response.failureCount > 0) {
                const failedTokens = [];
                response.responses.forEach((resp, idx) => {
                  if (!resp.success) {
                    failedTokens.push(batch[idx]);
                  }
                });
                // Delete invalid tokens from Firestore
                const deletePromises = failedTokens.map((token) =>
                  admin.firestore().collection("devices").doc(token).delete()
                );
                await Promise.all(deletePromises);
              }
            } catch (error) {
              logger.error(`Error sending batch ${i}:`, error);
              failureCount += batch.length;
            }
          }
        } else {
          // Send to provided tokens
          const batchSize = 500;
          for (let i = 0; i < tokens.length; i += batchSize) {
            const batch = tokens.slice(i, i + batchSize);
            try {
              const response = await admin.messaging().sendEachForMulticast({
                ...message,
                tokens: batch,
              });

              successCount += response.successCount;
              failureCount += response.failureCount;
            } catch (error) {
              logger.error(`Error sending batch ${i}:`, error);
              failureCount += batch.length;
            }
          }
        }
      } else if (target.startsWith("topic:")) {
        // Send to topic
        const topic = target.replace("topic:", "");
        try {
          await admin.messaging().send({
            ...message,
            topic: topic,
          });
          successCount = 1; // Topic sends are all-or-nothing
        } catch (error) {
          logger.error(`Error sending to topic ${topic}:`, error);
          failureCount = 1;
        }
      }

      // Update notification status
      await admin
        .firestore()
        .collection("broadcastNotifications")
        .doc(notificationId)
        .update({
          status: failureCount === 0 ? "sent" : "partial",
          sentCount: successCount,
          failedCount: failureCount,
          sentAt: admin.firestore.FieldValue.serverTimestamp(),
        });

      logger.info(`Notification sent successfully`, {
        notificationId,
        successCount,
        failureCount,
      });
    } catch (error) {
      logger.error(`Error processing notification ${notificationId}:`, error);
      await admin
        .firestore()
        .collection("broadcastNotifications")
        .doc(notificationId)
        .update({
          status: "failed",
          error: error.message,
        });
    }
  }
);

/**
 * OpenAI Proxy Function - Securely proxies OpenAI API requests
 * API key is stored in Firebase Functions environment variables
 * 
 * Usage:
 * POST /openaiChat
 * Body: {
 *   "messages": [{"role": "user", "content": "Hello"}],
 *   "model": "gpt-3.5-turbo",
 *   "max_tokens": 300,
 *   "temperature": 0.7
 * }
 */
exports.openaiChat = onRequest(
  {
    cors: true,
    maxInstances: 10,
    secrets: [openaiApiKeySecret], // Access the secret
  },
  async (req, res) => {
    // Set CORS headers
    res.set("Access-Control-Allow-Origin", "*");
    res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
    res.set("Access-Control-Allow-Headers", "Content-Type");

    // Handle preflight requests
    if (req.method === "OPTIONS") {
      res.status(204).send("");
      return;
    }

    // Only allow POST requests
    if (req.method !== "POST") {
      res.status(405).json({ error: "Method not allowed" });
      return;
    }

    try {
      // Get OpenAI API key from Firebase Functions secret
      const openaiApiKey = openaiApiKeySecret.value();
      
      if (!openaiApiKey) {
        logger.error("OPENAI_API_KEY secret not configured");
        res.status(500).json({
          error: "Server configuration error: OpenAI API key not set. Please configure OPENAI_API_KEY secret in Firebase Functions.",
        });
        return;
      }

      // Validate request body
      const { messages, model, max_tokens, temperature } = req.body;

      if (!messages || !Array.isArray(messages) || messages.length === 0) {
        res.status(400).json({
          error: "Invalid request: messages array is required",
        });
        return;
      }

      // Prepare OpenAI API request
      const openaiRequest = {
        model: model || "gpt-3.5-turbo",
        messages: messages,
        max_tokens: max_tokens || 300,
        temperature: temperature || 0.7,
      };

      logger.info("Proxying OpenAI request", {
        model: openaiRequest.model,
        messageCount: messages.length,
      });

      // Make request to OpenAI API
      const response = await axios.post(
        "https://api.openai.com/v1/chat/completions",
        openaiRequest,
        {
          headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${openaiApiKey}`,
          },
          timeout: 30000, // 30 seconds timeout
        }
      );

      // Return OpenAI response
      res.status(200).json(response.data);
    } catch (error) {
      logger.error("Error proxying OpenAI request", {
        error: error.message,
        stack: error.stack,
      });

      // Handle specific error cases
      if (error.response) {
        // OpenAI API returned an error
        const statusCode = error.response.status;
        const errorData = error.response.data;

        if (statusCode === 401) {
          res.status(401).json({
            error: "Invalid API key. Please check server configuration.",
          });
        } else if (statusCode === 429) {
          res.status(429).json({
            error: "Rate limit exceeded. Please try again later.",
          });
        } else {
          res.status(statusCode).json({
            error: errorData?.error?.message || "OpenAI API error",
          });
        }
      } else if (error.code === "ECONNABORTED") {
        res.status(504).json({
          error: "Request timeout. Please try again.",
        });
      } else {
        res.status(500).json({
          error: "Internal server error",
        });
      }
    }
  }
);
