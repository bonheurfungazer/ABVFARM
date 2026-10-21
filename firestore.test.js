const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user: cannot read rabbits", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("rabbits").get());
});

test("Authenticated user: can create and read their own rabbit", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const rabbitRef = aliceDb.collection("users").doc(ALICE_UID).collection("rabbits").doc("rabbit_1");

  await assertSucceeds(
    rabbitRef.set({
      userId: ALICE_UID,
      localId: 1,
      name: "Bugs",
      sex: "Male",
      dateOfBirth: Date.now(),
      breed: "Fauve de Bourgogne",
      cageNumber: "C-12",
      weight: 3.5,
      notes: "En bonne santé",
      isActive: true,
      status: "Actif",
      createdAt: new Date(),
    })
  );

  await assertSucceeds(rabbitRef.get());
});

test("Cross-user isolation: Bob cannot read Alice's rabbits", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("rabbits").doc("rabbit_1").set({
      userId: ALICE_UID,
      name: "Secret Rabbit",
      sex: "Female",
      createdAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("rabbits").doc("rabbit_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("rabbits").get());
});

test("Schema validation: rejects rabbit with missing name or invalid type", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const rabbitRef = aliceDb.collection("users").doc(ALICE_UID).collection("rabbits").doc("bad_rabbit");

  // Missing name
  await assertFails(
    rabbitRef.set({
      userId: ALICE_UID,
      sex: "Male",
      createdAt: new Date(),
    })
  );

  // Invalid sex (must be string)
  await assertFails(
    rabbitRef.set({
      userId: ALICE_UID,
      name: "Test",
      sex: 12345,
      createdAt: new Date(),
    })
  );
});

test("Authenticated user: can manage mating records, inventory and reminders", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Mating record
  const matingRef = aliceDb.collection("users").doc(ALICE_UID).collection("mating_records").doc("mating_1");
  await assertSucceeds(
    matingRef.set({
      userId: ALICE_UID,
      maleId: 1,
      femaleId: 2,
      matingDate: Date.now(),
      expectedBirthDate: Date.now() + 30 * 86400000,
      status: "Accouplé",
      notes: "Saillie réussie",
      createdAt: new Date(),
    })
  );

  // Inventory item
  const itemRef = aliceDb.collection("users").doc(ALICE_UID).collection("inventory_items").doc("item_1");
  await assertSucceeds(
    itemRef.set({
      userId: ALICE_UID,
      name: "Granulés élevage",
      category: "Alimentation",
      quantity: 50.0,
      unit: "kg",
      lowStockThreshold: 10.0,
      notes: "Sac de 25kg",
      createdAt: new Date(),
    })
  );

  // Reminder
  const reminderRef = aliceDb.collection("users").doc(ALICE_UID).collection("reminders").doc("rem_1");
  await assertSucceeds(
    reminderRef.set({
      userId: ALICE_UID,
      title: "Palpation",
      dueDate: Date.now() + 12 * 86400000,
      rabbitId: 2,
      category: "Palpation",
      isCompleted: false,
      createdAt: new Date(),
    })
  );
});
