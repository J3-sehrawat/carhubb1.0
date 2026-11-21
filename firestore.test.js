const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_001";
const ADMIN_EMAIL = "sehrawatj000@gmail.com";
const SHOP_APPROVED_UID = "shop_approved_101";
const SHOP_PENDING_UID = "shop_pending_202";
const SHOP_OTHER_UID = "shop_other_303";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msAgo = 5000) {
  return new Date(Date.now() - msAgo);
}

function futureDate(msAhead = 60000) {
  return new Date(Date.now() + msAhead);
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

async function seedUsers() {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const ts = pastDate(10000);
    await db.collection("users").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      name: "Wholesaler Admin",
      email: ADMIN_EMAIL,
      shopName: "AutoTrade Wholesale Hub",
      phone: "9876543210",
      city: "Delhi",
      role: "admin",
      status: "approved",
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("users").doc(SHOP_APPROVED_UID).set({
      userId: SHOP_APPROVED_UID,
      name: "Rajesh Sharma",
      email: "rajesh@sharmaauto.com",
      shopName: "Sharma Car Decor",
      phone: "9811223344",
      city: "Jaipur",
      role: "shopkeeper",
      status: "approved",
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("users").doc(SHOP_PENDING_UID).set({
      userId: SHOP_PENDING_UID,
      name: "Vikram Patel",
      email: "vikram@patelmotors.com",
      shopName: "Patel Auto Accessories",
      phone: "9922334455",
      city: "Surat",
      role: "shopkeeper",
      status: "pending",
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("users").doc(SHOP_OTHER_UID).set({
      userId: SHOP_OTHER_UID,
      name: "Amit Verma",
      email: "amit@vermaauto.com",
      shopName: "Verma Car Zone",
      phone: "9733445566",
      city: "Pune",
      role: "shopkeeper",
      status: "approved",
      createdAt: ts,
      updatedAt: ts,
    });
  });
}

// --- 1. UNAUTHENTICATED ACCESS DENIAL ---
test("Unauthenticated users cannot read or write any collection", async () => {
  await seedUsers();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("products").get());
  await assertFails(unauthDb.collection("categories").get());
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("orders").get());
  await assertFails(unauthDb.collection("settings").doc("store").get());
});

// --- 2. USER REGISTRATION, ROLE ESCALATION & PII ISOLATION ---
test("Shopkeeper registration enforces role=shopkeeper and status=pending; blocks self-escalation & email spoof", async () => {
  const ts = pastDate();
  const newShopDb = testEnv.authenticatedContext("new_shop_1", { email: "buyer@shop.com", email_verified: true }).firestore();

  // Valid shopkeeper registration (mirrors Kotlin UserProfile create payload; NO `id` field)
  await assertSucceeds(
    newShopDb.collection("users").doc("new_shop_1").set({
      userId: "new_shop_1",
      name: "Suresh Kumar",
      email: "buyer@shop.com",
      shopName: "Kumar Car Spares",
      phone: "9876500001",
      city: "Indore",
      role: "shopkeeper",
      status: "pending",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject self-assigned approved status
  const escalaterDb = testEnv.authenticatedContext("evil_1", { email: "evil@test.com", email_verified: true }).firestore();
  await assertFails(
    escalaterDb.collection("users").doc("evil_1").set({
      userId: "evil_1",
      name: "Evil Buyer",
      email: "evil@test.com",
      shopName: "Fake Shop",
      phone: "9876500002",
      city: "Mumbai",
      role: "shopkeeper",
      status: "approved",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject self-assigned admin role with spoofed UNVERIFIED admin email
  const spoofDb = testEnv.authenticatedContext("spoof_1", { email: ADMIN_EMAIL, email_verified: false }).firestore();
  await assertFails(
    spoofDb.collection("users").doc("spoof_1").set({
      userId: "spoof_1",
      name: "Spoofer",
      email: ADMIN_EMAIL,
      shopName: "Spoof Hub",
      phone: "9876500003",
      city: "Delhi",
      role: "admin",
      status: "approved",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject ghost field / @DocumentId `id` field
  const ghostDb = testEnv.authenticatedContext("ghost_1", { email: "g@shop.com", email_verified: true }).firestore();
  await assertFails(
    ghostDb.collection("users").doc("ghost_1").set({
      id: "ghost_1",
      userId: "ghost_1",
      name: "Ghost",
      email: "g@shop.com",
      shopName: "Ghost Shop",
      phone: "9876500004",
      city: "Delhi",
      role: "shopkeeper",
      status: "pending",
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("User PII isolation and tiered update actions", async () => {
  await seedUsers();
  const approvedDb = testEnv.authenticatedContext(SHOP_APPROVED_UID, { email: "rajesh@sharmaauto.com", email_verified: true }).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();

  // Owner can read own profile, CANNOT read another user's PII or list all users
  await assertSucceeds(approvedDb.collection("users").doc(SHOP_APPROVED_UID).get());
  await assertFails(approvedDb.collection("users").doc(SHOP_OTHER_UID).get());
  await assertFails(approvedDb.collection("users").get());

  // Owner can update own contact info, CANNOT change own status/role
  await assertSucceeds(
    approvedDb.collection("users").doc(SHOP_APPROVED_UID).update({
      shopName: "Sharma Premium Car Decor",
      city: "Jaipur",
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    approvedDb.collection("users").doc(SHOP_APPROVED_UID).update({
      role: "admin",
      updatedAt: pastDate(1000),
    })
  );

  // Admin can list users and approve/block shopkeeper accounts
  await assertSucceeds(adminDb.collection("users").get());
  await assertSucceeds(
    adminDb.collection("users").doc(SHOP_PENDING_UID).update({
      status: "approved",
      updatedAt: pastDate(1000),
    })
  );
});

// --- 3. PRODUCTS & CATEGORIES ACCESS CONTROL ---
test("Only admin writes products/categories; only approved buyers & admin read wholesale catalog", async () => {
  await seedUsers();
  const ts = pastDate(5000);
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const approvedDb = testEnv.authenticatedContext(SHOP_APPROVED_UID, { email: "rajesh@sharmaauto.com", email_verified: true }).firestore();
  const pendingDb = testEnv.authenticatedContext(SHOP_PENDING_UID, { email: "vikram@patelmotors.com", email_verified: true }).firestore();

  // Admin creates category and product
  await assertSucceeds(
    adminDb.collection("categories").doc("cat_seat_covers").set({
      name: "Seat Covers",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    adminDb.collection("products").doc("prod_001").set({
      name: "Nappa Diamond 7D Seat Cover",
      category: "Seat Covers",
      carModel: "Creta",
      price: 2450,
      moq: 5,
      inStock: true,
      description: "High-grade PU leather with double red stitching",
      imageUrls: ["data:image/jpeg;base64,AAAA"],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Reject future timestamp on product creation
  await assertFails(
    adminDb.collection("products").doc("prod_future").set({
      name: "Future Product",
      category: "Seat Covers",
      carModel: "Swift",
      price: 500,
      moq: 2,
      inStock: true,
      description: "",
      imageUrls: [],
      createdAt: futureDate(),
      updatedAt: futureDate(),
    })
  );

  // Admin quick stock toggle succeeds
  await assertSucceeds(
    adminDb.collection("products").doc("prod_001").update({
      inStock: false,
      updatedAt: pastDate(1000),
    })
  );

  // Approved shopkeeper can read products and categories, CANNOT write
  await assertSucceeds(approvedDb.collection("products").orderBy("createdAt", "desc").get());
  await assertSucceeds(approvedDb.collection("categories").orderBy("name", "asc").get());
  await assertFails(
    approvedDb.collection("products").doc("prod_001").update({
      price: 1,
      updatedAt: pastDate(1000),
    })
  );

  // Pending (unapproved) shopkeeper CANNOT read wholesale products or categories
  await assertFails(pendingDb.collection("products").get());
  await assertFails(pendingDb.collection("categories").get());
});

// --- 4. ORDERS ISOLATION & LIFECYCLE ---
test("Approved shopkeepers create and read only their own orders; admin manages all orders", async () => {
  await seedUsers();
  const ts = pastDate(4000);
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const approvedDb = testEnv.authenticatedContext(SHOP_APPROVED_UID, { email: "rajesh@sharmaauto.com", email_verified: true }).firestore();
  const otherDb = testEnv.authenticatedContext(SHOP_OTHER_UID, { email: "amit@vermaauto.com", email_verified: true }).firestore();
  const pendingDb = testEnv.authenticatedContext(SHOP_PENDING_UID, { email: "vikram@patelmotors.com", email_verified: true }).firestore();

  const validOrder = {
    shopkeeperId: SHOP_APPROVED_UID,
    shopName: "Sharma Car Decor",
    phone: "9811223344",
    city: "Jaipur",
    items: [
      {
        productId: "prod_001",
        name: "Nappa Diamond 7D Seat Cover",
        carModel: "Creta",
        price: 2450,
        qty: 5,
        imageUrl: "",
      },
    ],
    totalAmount: 12250,
    status: "Pending",
    createdAt: ts,
    updatedAt: ts,
  };

  // Approved shopkeeper creates order with status Pending
  await assertSucceeds(approvedDb.collection("orders").doc("order_001").set(validOrder));

  // Reject creating order with non-Pending status
  await assertFails(
    approvedDb.collection("orders").doc("order_bad_status").set({
      ...validOrder,
      status: "Delivered",
    })
  );

  // Pending shopkeeper CANNOT place orders
  await assertFails(
    pendingDb.collection("orders").doc("order_pending_user").set({
      ...validOrder,
      shopkeeperId: SHOP_PENDING_UID,
    })
  );

  // Shopkeeper filtered Kotlin query succeeds; unfiltered list query fails
  await assertSucceeds(
    approvedDb.collection("orders").where("shopkeeperId", "==", SHOP_APPROVED_UID).get()
  );
  await assertFails(approvedDb.collection("orders").get());

  // Cross-shopkeeper isolation: other approved shopkeeper cannot read Rajesh's order
  await assertFails(otherDb.collection("orders").doc("order_001").get());
  await assertFails(
    otherDb.collection("orders").where("shopkeeperId", "==", SHOP_APPROVED_UID).get()
  );

  // Shopkeeper cannot update order status
  await assertFails(
    approvedDb.collection("orders").doc("order_001").update({
      status: "Confirmed",
      updatedAt: pastDate(1000),
    })
  );

  // Admin can list all orders and update status
  await assertSucceeds(adminDb.collection("orders").orderBy("createdAt", "desc").get());
  await assertSucceeds(
    adminDb.collection("orders").doc("order_001").update({
      status: "Dispatched",
      updatedAt: pastDate(1000),
    })
  );
});

// --- 5. STORE SETTINGS ---
test("Only admin updates store settings; approved buyers can read store doc", async () => {
  await seedUsers();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const approvedDb = testEnv.authenticatedContext(SHOP_APPROVED_UID, { email: "rajesh@sharmaauto.com", email_verified: true }).firestore();
  const pendingDb = testEnv.authenticatedContext(SHOP_PENDING_UID, { email: "vikram@patelmotors.com", email_verified: true }).firestore();

  await assertSucceeds(
    adminDb.collection("settings").doc("store").set({
      businessName: "AutoTrade Wholesale Hub",
      whatsappPhone: "919876543210",
      city: "Delhi",
      updatedAt: pastDate(1000),
    })
  );

  await assertSucceeds(approvedDb.collection("settings").doc("store").get());
  await assertFails(pendingDb.collection("settings").doc("store").get());
  await assertFails(approvedDb.collection("settings").get());
  await assertFails(
    approvedDb.collection("settings").doc("store").update({
      whatsappPhone: "910000000000",
      updatedAt: pastDate(500),
    })
  );
});

// --- 6. FULL END-TO-END DATA SAVE VERIFICATION ---
test("End-to-end save lifecycle: admin profile, store settings update, category update, 4-photo product CRUD, order progression", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const ts1 = pastDate(8000);
  const ts2 = pastDate(3000);

  // 1. Admin creates own profile
  await assertSucceeds(
    adminDb.collection("users").doc(ADMIN_UID).set({
      userId: ADMIN_UID,
      name: "Admin Owner",
      email: ADMIN_EMAIL,
      shopName: "AutoTrade Hub",
      phone: "9876543210",
      city: "Delhi",
      role: "admin",
      status: "approved",
      createdAt: ts1,
      updatedAt: ts1,
    })
  );

  // 2. Store settings create + subsequent update
  await assertSucceeds(
    adminDb.collection("settings").doc("store").set({
      businessName: "AutoTrade Hub",
      whatsappPhone: "9876543210",
      city: "Delhi",
      updatedAt: ts1,
    })
  );
  await assertSucceeds(
    adminDb.collection("settings").doc("store").set({
      businessName: "AutoTrade Wholesale Hub",
      whatsappPhone: "919876543210",
      city: "New Delhi",
      updatedAt: ts2,
    })
  );

  // 3. Category create + safe duplicate update (preserves createdAt)
  await assertSucceeds(
    adminDb.collection("categories").doc("cat_seat_covers").set({
      name: "Seat Covers",
      createdAt: ts1,
      updatedAt: ts1,
    })
  );
  await assertSucceeds(
    adminDb.collection("categories").doc("cat_seat_covers").update({
      name: "Seat Covers",
      updatedAt: ts2,
    })
  );

  // 4. Product create (4 photos) -> full edit -> stock toggle -> delete
  await assertSucceeds(
    adminDb.collection("products").doc("prod_full_1").set({
      name: "7D Luxury Floor Mat",
      category: "Seat Covers",
      carModel: "Thar",
      price: 1899.5,
      moq: 4,
      inStock: true,
      description: "Waterproof anti-skid 7D mat set",
      imageUrls: [
        "data:image/jpeg;base64,AAA1",
        "data:image/jpeg;base64,AAA2",
        "data:image/jpeg;base64,AAA3",
        "data:image/jpeg;base64,AAA4",
      ],
      createdAt: ts1,
      updatedAt: ts1,
    })
  );
  await assertSucceeds(
    adminDb.collection("products").doc("prod_full_1").update({
      name: "7D Luxury Floor Mat Pro",
      category: "Seat Covers",
      carModel: "Thar",
      price: 1999,
      moq: 6,
      inStock: true,
      description: "Updated waterproof 7D mat set",
      imageUrls: ["data:image/jpeg;base64,AAA1"],
      updatedAt: ts2,
    })
  );
  await assertSucceeds(
    adminDb.collection("products").doc("prod_full_1").update({
      inStock: false,
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(adminDb.collection("products").doc("prod_full_1").delete());
});

// --- 7. BILLING, BUSINESS PROFILE & INVOICE ISOLATION ---
test("BusinessProfile is admin-only; Invoices allow admin full CRUD and approved shopkeepers own-read only", async () => {
  await seedUsers();
  const ts1 = pastDate(5000);
  const ts2 = pastDate(1000);
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const approvedDb = testEnv.authenticatedContext(SHOP_APPROVED_UID, { email: "rajesh@sharmaauto.com", email_verified: true }).firestore();
  const otherDb = testEnv.authenticatedContext(SHOP_OTHER_UID, { email: "amit@vermaauto.com", email_verified: true }).firestore();
  const pendingDb = testEnv.authenticatedContext(SHOP_PENDING_UID, { email: "vikram@patelmotors.com", email_verified: true }).firestore();

  // Business profile: Admin can create/read/update; shopkeepers cannot read or write
  await assertSucceeds(
    adminDb.collection("settings").doc("businessProfile").set({
      firmName: "AutoTrade Wholesale Pvt Ltd",
      address: "Kashmere Gate, Delhi 110006",
      phone: "9876543210",
      gstin: "07AABCU9603R1ZM",
      upiId: "autotrade@upi",
      invoicePrefix: "INV",
      nextInvoiceNumber: 1,
      updatedAt: ts1,
    })
  );
  await assertSucceeds(adminDb.collection("settings").doc("businessProfile").get());
  await assertFails(approvedDb.collection("settings").doc("businessProfile").get());
  await assertFails(
    approvedDb.collection("settings").doc("businessProfile").update({
      nextInvoiceNumber: 99,
      updatedAt: ts2,
    })
  );

  // Product with HSN + GST% succeeds
  await assertSucceeds(
    adminDb.collection("products").doc("prod_gst_1").set({
      name: "LED Projector Fog Lamp",
      category: "Lighting",
      carModel: "Creta",
      price: 1650,
      moq: 2,
      inStock: true,
      description: "IP67 waterproof tri-color LED",
      hsnCode: "85122010",
      gstPercent: 18,
      imageUrls: [],
      createdAt: ts1,
      updatedAt: ts1,
    })
  );

  // Admin creates GST invoice for Rajesh (SHOP_APPROVED_UID)
  const validInvoice = {
    invoiceNumber: "INV-0001",
    orderId: "order_101",
    billType: "GST Invoice",
    shopkeeperId: SHOP_APPROVED_UID,
    shopName: "Sharma Car Decor",
    phone: "9811223344",
    city: "Jaipur",
    shopGstin: "08AABCS1234F1Z5",
    firmName: "AutoTrade Wholesale Pvt Ltd",
    firmAddress: "Kashmere Gate, Delhi 110006",
    firmPhone: "9876543210",
    firmGstin: "07AABCU9603R1ZM",
    firmUpiId: "autotrade@upi",
    items: [
      {
        productId: "prod_gst_1",
        name: "LED Projector Fog Lamp",
        hsnCode: "85122010",
        qty: 4,
        rate: 1650,
        gstPercent: 18,
        lineTotal: 6600,
      },
    ],
    subtotal: 6600,
    discountAmount: 200,
    taxType: "IGST",
    taxAmount: 1152,
    transportCharges: 150,
    grandTotal: 7702,
    paidAmount: 2000,
    balanceDue: 5702,
    paymentStatus: "Partial",
    paymentHistory: [
      { amount: 2000, mode: "UPI", date: "20 Nov 2026", note: "Advance" },
    ],
    createdAt: ts1,
    updatedAt: ts1,
  };

  await assertSucceeds(adminDb.collection("invoices").doc("inv_001").set(validInvoice));

  // Shopkeeper CANNOT create or mutate invoices
  await assertFails(approvedDb.collection("invoices").doc("inv_fake").set(validInvoice));
  await assertFails(
    approvedDb.collection("invoices").doc("inv_001").update({
      paidAmount: 7702,
      balanceDue: 0,
      paymentStatus: "Paid",
      paymentHistory: [],
      updatedAt: ts2,
    })
  );

  // Approved shopkeeper CAN list own invoices via whereEqualTo("shopkeeperId", uid) and get own invoice
  await assertSucceeds(approvedDb.collection("invoices").doc("inv_001").get());
  await assertSucceeds(
    approvedDb.collection("invoices").where("shopkeeperId", "==", SHOP_APPROVED_UID).get()
  );

  // Unfiltered invoice list & cross-shopkeeper reads FAIL
  await assertFails(approvedDb.collection("invoices").get());
  await assertFails(otherDb.collection("invoices").doc("inv_001").get());
  await assertFails(
    otherDb.collection("invoices").where("shopkeeperId", "==", SHOP_APPROVED_UID).get()
  );
  await assertFails(pendingDb.collection("invoices").where("shopkeeperId", "==", SHOP_PENDING_UID).get());

  // Admin records payment & marks invoice Paid
  await assertSucceeds(
    adminDb.collection("invoices").doc("inv_001").update({
      paidAmount: 7702,
      balanceDue: 0,
      paymentStatus: "Paid",
      paymentHistory: [
        { amount: 2000, mode: "UPI", date: "20 Nov 2026", note: "Advance" },
        { amount: 5702, mode: "Cash", date: "21 Nov 2026", note: "Settled" },
      ],
      updatedAt: ts2,
    })
  );

  // Shopkeeper can update own GSTIN in profile
  await assertSucceeds(
    approvedDb.collection("users").doc(SHOP_APPROVED_UID).update({
      gstin: "08AABCS1234F1Z5",
      updatedAt: ts2,
    })
  );
});

// --- 8. NEW ARRIVALS CONTROLS ---
test("Only admin controls New Arrivals section settings and product newArrival toggle; approved buyers can read", async () => {
  await seedUsers();
  const ts1 = pastDate(5000);
  const ts2 = pastDate(1000);
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const approvedDb = testEnv.authenticatedContext(SHOP_APPROVED_UID, { email: "rajesh@sharmaauto.com", email_verified: true }).firestore();

  // Admin saves store settings with New Arrivals section controls
  await assertSucceeds(
    adminDb.collection("settings").doc("store").set({
      businessName: "AutoTrade Wholesale Hub",
      whatsappPhone: "919876543210",
      city: "Delhi",
      showNewArrivals: true,
      newArrivalsTitle: "Fresh New Stock Today",
      newArrivalsLimit: 12,
      updatedAt: ts1,
    })
  );

  // Approved buyer can read New Arrivals settings, CANNOT modify them
  await assertSucceeds(approvedDb.collection("settings").doc("store").get());
  await assertFails(
    approvedDb.collection("settings").doc("store").update({
      showNewArrivals: false,
      updatedAt: ts2,
    })
  );

  // Admin creates product with newArrival = true and toggles it off
  await assertSucceeds(
    adminDb.collection("products").doc("prod_new_1").set({
      name: "Ambient 64-Color Car LED Kit",
      category: "Lighting",
      carModel: "Universal",
      price: 1250,
      moq: 5,
      inStock: true,
      newArrival: true,
      description: "App-controlled RGB interior strips",
      hsnCode: "85122010",
      gstPercent: 18,
      imageUrls: [],
      createdAt: ts1,
      updatedAt: ts1,
    })
  );

  await assertSucceeds(
    adminDb.collection("products").doc("prod_new_1").update({
      newArrival: false,
      updatedAt: ts2,
    })
  );

  // Shopkeeper CANNOT toggle product newArrival flag
  await assertFails(
    approvedDb.collection("products").doc("prod_new_1").update({
      newArrival: true,
      updatedAt: pastDate(200),
    })
  );
});

// --- 9. FCM TOKEN REGISTRATION & SHOPKEEPER NOTIFICATIONS ISOLATION ---
test("Owner updates own FCM token & preferences; only admin creates notifications; approved shopkeepers read own + ALL_BUYERS only", async () => {
  await seedUsers();
  const ts1 = pastDate(4000);
  const ts2 = pastDate(1000);
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const approvedDb = testEnv.authenticatedContext(SHOP_APPROVED_UID, { email: "rajesh@sharmaauto.com", email_verified: true }).firestore();
  const otherDb = testEnv.authenticatedContext(SHOP_OTHER_UID, { email: "amit@vermaauto.com", email_verified: true }).firestore();
  const pendingDb = testEnv.authenticatedContext(SHOP_PENDING_UID, { email: "vikram@patelmotors.com", email_verified: true }).firestore();

  // Shopkeeper registers own FCM token and toggles notification preferences
  await assertSucceeds(
    approvedDb.collection("users").doc(SHOP_APPROVED_UID).update({
      fcmToken: "fcm_device_token_rajesh_1234567890",
      notifyOrderUpdates: true,
      notifyNewProducts: true,
      updatedAt: ts1,
    })
  );

  // Other shopkeeper CANNOT modify Rajesh's FCM token
  await assertFails(
    otherDb.collection("users").doc(SHOP_APPROVED_UID).update({
      fcmToken: "hijacked_token",
      updatedAt: ts2,
    })
  );

  // Admin publishes an order_status notification targeted to SHOP_APPROVED_UID
  await assertSucceeds(
    adminDb.collection("notifications").doc("notif_order_1").set({
      type: "order_status",
      targetUserId: SHOP_APPROVED_UID,
      targetTopic: `user_${SHOP_APPROVED_UID}`,
      targetFcmToken: "fcm_device_token_rajesh_1234567890",
      title: "Order #ORDER1 Dispatched",
      body: "Your wholesale order of ₹12,250 is now Dispatched.",
      orderId: "order_001",
      productId: "",
      status: "Dispatched",
      createdAt: ts1,
    })
  );

  // Admin publishes a broadcast new_product notification for ALL_BUYERS
  await assertSucceeds(
    adminDb.collection("notifications").doc("notif_prod_1").set({
      type: "new_product",
      targetUserId: "ALL_BUYERS",
      targetTopic: "new_products",
      targetFcmToken: "",
      title: "New Product: 7D Leather Seat Cover",
      body: "Creta • Seat Covers • ₹2,450 (MOQ 5)",
      orderId: "",
      productId: "prod_001",
      status: "",
      createdAt: ts2,
    })
  );

  // Shopkeeper CANNOT create or delete notifications
  await assertFails(
    approvedDb.collection("notifications").doc("notif_spoof").set({
      type: "new_product",
      targetUserId: "ALL_BUYERS",
      targetTopic: "new_products",
      title: "Fake Promo",
      body: "Spoofed notification",
      orderId: "",
      productId: "",
      status: "",
      createdAt: ts2,
    })
  );
  await assertFails(approvedDb.collection("notifications").doc("notif_order_1").delete());

  // Approved shopkeeper reads own + ALL_BUYERS using exact Kotlin whereIn query
  await assertSucceeds(
    approvedDb.collection("notifications").where("targetUserId", "in", [SHOP_APPROVED_UID, "ALL_BUYERS"]).get()
  );
  await assertSucceeds(approvedDb.collection("notifications").doc("notif_order_1").get());
  await assertSucceeds(approvedDb.collection("notifications").doc("notif_prod_1").get());

  // Unfiltered list query FAILS for shopkeeper
  await assertFails(approvedDb.collection("notifications").get());

  // Cross-shopkeeper isolation: Amit can read ALL_BUYERS broadcast, CANNOT read Rajesh's order notification
  await assertSucceeds(otherDb.collection("notifications").doc("notif_prod_1").get());
  await assertFails(otherDb.collection("notifications").doc("notif_order_1").get());
  await assertFails(
    otherDb.collection("notifications").where("targetUserId", "in", [SHOP_APPROVED_UID, "ALL_BUYERS"]).get()
  );

  // Pending shopkeeper CANNOT read any notifications
  await assertFails(pendingDb.collection("notifications").doc("notif_prod_1").get());
  await assertFails(
    pendingDb.collection("notifications").where("targetUserId", "in", [SHOP_PENDING_UID, "ALL_BUYERS"]).get()
  );
});

// --- 10. INVENTORY LEVEL MONITORING & LOW STOCK THRESHOLD ---
test("Only admin configures lowStockThreshold in businessProfile and updates product stockQuantity", async () => {
  await seedUsers();
  const ts1 = pastDate(4000);
  const ts2 = pastDate(1000);
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const approvedDb = testEnv.authenticatedContext(SHOP_APPROVED_UID, { email: "rajesh@sharmaauto.com", email_verified: true }).firestore();

  // Admin creates businessProfile with lowStockThreshold
  await assertSucceeds(
    adminDb.collection("settings").doc("businessProfile").set({
      firmName: "AutoTrade Wholesale Pvt Ltd",
      address: "Kashmere Gate, Delhi",
      phone: "9876543210",
      gstin: "07AABCU9603R1ZM",
      upiId: "autotrade@upi",
      invoicePrefix: "INV",
      nextInvoiceNumber: 1,
      lowStockThreshold: 12,
      updatedAt: ts1,
    })
  );

  // Reject invalid lowStockThreshold <= 0
  await assertFails(
    adminDb.collection("settings").doc("businessProfile").update({
      lowStockThreshold: 0,
      updatedAt: ts2,
    })
  );

  // Admin creates product with explicit stockQuantity
  await assertSucceeds(
    adminDb.collection("products").doc("prod_inv_1").set({
      name: "Carbon Steering Wheel Cover",
      category: "Steering Covers",
      carModel: "Universal",
      price: 420,
      moq: 5,
      inStock: true,
      stockQuantity: 15,
      newArrival: true,
      description: "Anti-slip perforated grip",
      hsnCode: "87089400",
      gstPercent: 18,
      imageUrls: [],
      createdAt: ts1,
      updatedAt: ts1,
    })
  );

  // Admin quick stock quantity adjustment succeeds (both low stock and zero-out)
  await assertSucceeds(
    adminDb.collection("products").doc("prod_inv_1").update({
      stockQuantity: 4,
      inStock: true,
      updatedAt: ts2,
    })
  );
  await assertSucceeds(
    adminDb.collection("products").doc("prod_inv_1").update({
      stockQuantity: 0,
      inStock: false,
      updatedAt: pastDate(500),
    })
  );

  // Reject negative stockQuantity
  await assertFails(
    adminDb.collection("products").doc("prod_inv_1").update({
      stockQuantity: -1,
      updatedAt: pastDate(300),
    })
  );

  // Shopkeeper CANNOT modify stockQuantity or businessProfile lowStockThreshold
  await assertFails(
    approvedDb.collection("products").doc("prod_inv_1").update({
      stockQuantity: 999,
      inStock: true,
      updatedAt: pastDate(200),
    })
  );
  await assertFails(
    approvedDb.collection("settings").doc("businessProfile").update({
      lowStockThreshold: 1,
      updatedAt: pastDate(200),
    })
  );
});

// --- 11. CATALOG THEME SETTINGS (PRIMARY BRAND COLOR) ---
test("Only admin sets valid #RRGGBB primaryColorHex in store settings; approved buyers read live theme", async () => {
  await seedUsers();
  const ts1 = pastDate(4000);
  const ts2 = pastDate(1000);
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const approvedDb = testEnv.authenticatedContext(SHOP_APPROVED_UID, { email: "rajesh@sharmaauto.com", email_verified: true }).firestore();

  // Admin creates store settings with a valid primaryColorHex
  await assertSucceeds(
    adminDb.collection("settings").doc("store").set({
      businessName: "AutoTrade Wholesale Hub",
      whatsappPhone: "919876543210",
      city: "Delhi",
      showNewArrivals: true,
      newArrivalsTitle: "Newly Listed Stock",
      newArrivalsLimit: 10,
      primaryColorHex: "#2563EB",
      updatedAt: ts1,
    })
  );

  // Admin updates primaryColorHex to another valid 6-digit hex
  await assertSucceeds(
    adminDb.collection("settings").doc("store").update({
      primaryColorHex: "#059669",
      updatedAt: ts2,
    })
  );

  // Reject malformed primaryColorHex values
  await assertFails(
    adminDb.collection("settings").doc("store").update({
      primaryColorHex: "blue",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    adminDb.collection("settings").doc("store").update({
      primaryColorHex: "#123",
      updatedAt: pastDate(400),
    })
  );

  // Approved shopkeeper can read live catalog theme, CANNOT mutate it
  await assertSucceeds(approvedDb.collection("settings").doc("store").get());
  await assertFails(
    approvedDb.collection("settings").doc("store").update({
      primaryColorHex: "#7C3AED",
      updatedAt: pastDate(200),
    })
  );
});

// --- 12. ADMIN ACTIVITY LOGS (IMMUTABLE AUDIT TRAIL) ---
test("Admin activityLogs are strictly admin-only, actor-bound, and immutable (no update or delete)", async () => {
  await seedUsers();
  const ts1 = pastDate(5000);
  const ts2 = pastDate(2000);
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const approvedDb = testEnv.authenticatedContext(SHOP_APPROVED_UID, { email: "rajesh@sharmaauto.com", email_verified: true }).firestore();
  const pendingDb = testEnv.authenticatedContext(SHOP_PENDING_UID, { email: "vikram@patelmotors.com", email_verified: true }).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  const validLog = {
    action: "price_changed",
    category: "pricing",
    actorUid: ADMIN_UID,
    actorEmail: ADMIN_EMAIL,
    actorName: "Wholesaler Admin",
    targetId: "prod_001",
    targetName: "Nappa Diamond 7D Seat Cover",
    summary: "Updated wholesale price from ₹2,450 to ₹2,750",
    previousValue: "₹2,450",
    newValue: "₹2,750",
    createdAt: ts1,
  };

  // Admin can append valid activity log bound to own actorUid
  await assertSucceeds(adminDb.collection("activityLogs").doc("log_price_1").set(validLog));

  // Admin can append product_added and shopkeeper_approved entries
  await assertSucceeds(
    adminDb.collection("activityLogs").doc("log_prod_1").set({
      action: "product_added",
      category: "product",
      actorUid: ADMIN_UID,
      actorEmail: ADMIN_EMAIL,
      actorName: "Wholesaler Admin",
      targetId: "prod_002",
      targetName: "Carbon Steering Cover",
      summary: "Added Carbon Steering Cover at ₹420 (MOQ 5)",
      previousValue: "",
      newValue: "₹420",
      createdAt: ts2,
    })
  );
  await assertSucceeds(
    adminDb.collection("activityLogs").doc("log_shop_1").set({
      action: "shopkeeper_approved",
      category: "shopkeeper",
      actorUid: ADMIN_UID,
      actorEmail: ADMIN_EMAIL,
      actorName: "Wholesaler Admin",
      targetId: SHOP_PENDING_UID,
      targetName: "Patel Auto Accessories",
      summary: "Approved shopkeeper Patel Auto Accessories (Surat)",
      previousValue: "Pending",
      newValue: "Approved",
      createdAt: ts2,
    })
  );

  // Admin can read and list activityLogs ordered by createdAt desc (mirrors Kotlin query)
  await assertSucceeds(adminDb.collection("activityLogs").doc("log_price_1").get());
  await assertSucceeds(adminDb.collection("activityLogs").orderBy("createdAt", "desc").limit(100).get());

  // Reject actorUid spoofing even by an admin
  await assertFails(
    adminDb.collection("activityLogs").doc("log_spoof_actor").set({
      ...validLog,
      actorUid: "another_admin_uid",
    })
  );

  // Reject unknown action enum value
  await assertFails(
    adminDb.collection("activityLogs").doc("log_bad_action").set({
      ...validLog,
      action: "arbitrary_hack",
    })
  );

  // Strictly immutable: even Admin CANNOT update or delete an audit log entry
  await assertFails(
    adminDb.collection("activityLogs").doc("log_price_1").update({
      summary: "Tampered summary",
    })
  );
  await assertFails(adminDb.collection("activityLogs").doc("log_price_1").delete());

  // Approved shopkeeper, pending shopkeeper, and unauthenticated users CANNOT read, list, or write activityLogs
  await assertFails(approvedDb.collection("activityLogs").get());
  await assertFails(approvedDb.collection("activityLogs").doc("log_price_1").get());
  await assertFails(
    approvedDb.collection("activityLogs").doc("log_buyer_write").set({
      ...validLog,
      actorUid: SHOP_APPROVED_UID,
    })
  );
  await assertFails(pendingDb.collection("activityLogs").get());
  await assertFails(unauthDb.collection("activityLogs").get());
});

// --- 14. AUTOMATIC STOCK DEDUCTION ON ORDER DELIVERY & BILL GENERATION ---
test("Admin can deduct product stock and set stockDeducted on orders/invoices; shopkeepers cannot", async () => {
  await seedUsers();
  const ts1 = pastDate(8000);
  const ts2 = pastDate(2000);

  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const approvedDb = testEnv.authenticatedContext(SHOP_APPROVED_UID, { email: "rajesh@sharmaauto.com", email_verified: true }).firestore();

  // Seed a product with stockQuantity = 10 and a pending order for qty = 2
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("products").doc("prod_stock_1").set({
      name: "7D Floor Mat Swift",
      category: "7D/Floor Mats",
      carModel: "Swift",
      price: 1850,
      moq: 1,
      inStock: true,
      stockQuantity: 10,
      newArrival: false,
      description: "Custom fit 7D floor mats",
      hsnCode: "8708",
      gstPercent: 18,
      imageUrls: [],
      createdAt: ts1,
      updatedAt: ts1,
    });
    await db.collection("orders").doc("order_stock_1").set({
      shopkeeperId: SHOP_APPROVED_UID,
      shopName: "Sharma Car Decor",
      phone: "9811223344",
      city: "Jaipur",
      items: [{ productId: "prod_stock_1", name: "7D Floor Mat Swift", carModel: "Swift", price: 1850, qty: 2, imageUrl: "" }],
      totalAmount: 3700,
      status: "Pending",
      createdAt: ts1,
      updatedAt: ts1,
    });
  });

  // Shopkeeper cannot forge stockDeducted = true on order create
  await assertFails(
    approvedDb.collection("orders").doc("order_forged_deduct").set({
      shopkeeperId: SHOP_APPROVED_UID,
      shopName: "Sharma Car Decor",
      phone: "9811223344",
      city: "Jaipur",
      items: [{ productId: "prod_stock_1", name: "7D Floor Mat Swift", carModel: "Swift", price: 1850, qty: 1, imageUrl: "" }],
      totalAmount: 1850,
      status: "Pending",
      stockDeducted: true,
      createdAt: ts2,
      updatedAt: ts2,
    })
  );

  // Admin can mark order Delivered + stockDeducted = true and decrement product stock
  await assertSucceeds(
    adminDb.collection("orders").doc("order_stock_1").update({
      status: "Delivered",
      stockDeducted: true,
      updatedAt: ts2,
    })
  );
  await assertSucceeds(
    adminDb.collection("products").doc("prod_stock_1").update({
      stockQuantity: 8,
      inStock: true,
      updatedAt: ts2,
    })
  );

  // Admin can create an invoice with stockDeducted = true and link it to an order
  await assertSucceeds(
    adminDb.collection("invoices").doc("inv_stock_1").set({
      invoiceNumber: "INV-0099",
      orderId: "order_stock_1",
      billType: "GST Invoice",
      shopkeeperId: SHOP_APPROVED_UID,
      shopName: "Sharma Car Decor",
      phone: "9811223344",
      city: "Jaipur",
      shopGstin: "",
      firmName: "AutoTrade Wholesale",
      firmAddress: "Delhi",
      firmPhone: "9876543210",
      firmGstin: "",
      firmUpiId: "",
      items: [{ productId: "prod_stock_1", name: "7D Floor Mat Swift", hsnCode: "8708", qty: 1, rate: 1850, gstPercent: 18, lineTotal: 1850 }],
      subtotal: 1850,
      discountAmount: 0,
      taxType: "CGST+SGST",
      taxAmount: 333,
      transportCharges: 0,
      grandTotal: 2183,
      paidAmount: 0,
      balanceDue: 2183,
      paymentStatus: "Unpaid",
      paymentHistory: [],
      stockDeducted: true,
      createdAt: ts2,
      updatedAt: ts2,
    })
  );
  await assertSucceeds(
    adminDb.collection("orders").doc("order_stock_1").update({
      invoiceId: "inv_stock_1",
      stockDeducted: true,
      updatedAt: ts2,
    })
  );

  // Shopkeeper cannot update order stockDeducted or product stockQuantity
  await assertFails(
    approvedDb.collection("orders").doc("order_stock_1").update({
      stockDeducted: false,
      updatedAt: ts2,
    })
  );
  await assertFails(
    approvedDb.collection("products").doc("prod_stock_1").update({
      stockQuantity: 100,
      inStock: true,
      updatedAt: ts2,
    })
  );
});


