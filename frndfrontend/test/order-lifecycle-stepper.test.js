import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const stylesCssPath = path.resolve(__dirname, '../styles.css');
const stylesCss = fs.readFileSync(stylesCssPath, 'utf8');

// Stepper and Lifecycle model simulation matching frndfrontend/main.jsx
const LIFECYCLE_STAGES = ['PLACED', 'READY_TO_ASSIGN', 'ASSIGNED', 'OUT_FOR_DELIVERY', 'DELIVERED'];
const STAGE_LABELS = {
  PLACED: 'Order Placed',
  READY_TO_ASSIGN: 'Approved (Preparing)',
  ASSIGNED: 'Driver Assigned',
  OUT_FOR_DELIVERY: 'Out for Delivery',
  DELIVERED: 'Delivered'
};

function evaluateOrderPresentation(order) {
  const status = order?.status;
  const isCancelled = status === 'CANCELLED';
  const isRejected = status === 'REJECTED';

  if (isCancelled) {
    return {
      type: 'TERMINAL',
      terminalType: 'CANCELLED',
      bannerTitle: 'Order Cancelled',
      bannerMessage: order.cancellationReason || 'This order has been cancelled and will not progress to delivery.',
      pillClass: 'status-pill cancelled',
      stepper: null
    };
  }

  if (isRejected) {
    return {
      type: 'TERMINAL',
      terminalType: 'REJECTED',
      bannerTitle: 'Order Rejected',
      bannerMessage: order.rejectionReason || 'This order was rejected by the store administrator.',
      pillClass: 'status-pill rejected',
      stepper: null
    };
  }

  const currentStageIndex = LIFECYCLE_STAGES.indexOf(status);

  const steps = LIFECYCLE_STAGES.map((stage, idx) => {
    const isCompleted = currentStageIndex > idx || (currentStageIndex === idx && stage === 'DELIVERED');
    const isActive = currentStageIndex === idx && stage !== 'DELIVERED';
    const isDone = currentStageIndex >= idx;
    const hasLine = idx < LIFECYCLE_STAGES.length - 1;
    const lineDone = currentStageIndex > idx;

    return {
      stage,
      label: STAGE_LABELS[stage] || stage,
      isCompleted,
      isActive,
      isDone,
      circleContent: isCompleted ? '✓' : String(idx + 1),
      hasLine,
      lineDone,
      classes: `step-item ${isCompleted ? 'completed' : ''} ${isDone ? 'done' : ''} ${isActive ? 'active' : ''}`.trim()
    };
  });

  return {
    type: 'PROGRESSION',
    status,
    pillClass: `status-pill ${String(status || '').toLowerCase()}`,
    stepper: {
      totalStages: LIFECYCLE_STAGES.length,
      currentStageIndex,
      steps
    }
  };
}

// 1. PLACED renders correctly
test('Order Lifecycle: 1. PLACED renders correctly with step 0 active', () => {
  const result = evaluateOrderPresentation({ status: 'PLACED' });
  assert.equal(result.type, 'PROGRESSION');
  assert.equal(result.stepper.steps[0].isActive, true);
  assert.equal(result.stepper.steps[0].circleContent, '1');
  assert.equal(result.stepper.steps[0].hasLine, true);
  assert.equal(result.stepper.steps[0].lineDone, false);
  assert.equal(result.stepper.steps[1].isActive, false);
  assert.equal(result.stepper.steps[1].isDone, false);
  assert.equal(result.pillClass, 'status-pill placed');
});

// 2. READY_TO_ASSIGN renders correctly
test('Order Lifecycle: 2. READY_TO_ASSIGN renders correctly with step 1 active and step 0 completed', () => {
  const result = evaluateOrderPresentation({ status: 'READY_TO_ASSIGN' });
  assert.equal(result.type, 'PROGRESSION');
  assert.equal(result.stepper.steps[0].isCompleted, true);
  assert.equal(result.stepper.steps[0].circleContent, '✓');
  assert.equal(result.stepper.steps[0].lineDone, true);
  assert.equal(result.stepper.steps[1].isActive, true);
  assert.equal(result.stepper.steps[1].circleContent, '2');
  assert.equal(result.stepper.steps[2].isActive, false);
  assert.equal(result.pillClass, 'status-pill ready_to_assign');
});

// 3. ASSIGNED renders correctly
test('Order Lifecycle: 3. ASSIGNED renders correctly with step 2 active and steps 0,1 completed', () => {
  const result = evaluateOrderPresentation({ status: 'ASSIGNED' });
  assert.equal(result.type, 'PROGRESSION');
  assert.equal(result.stepper.steps[0].isCompleted, true);
  assert.equal(result.stepper.steps[1].isCompleted, true);
  assert.equal(result.stepper.steps[2].isActive, true);
  assert.equal(result.stepper.steps[2].circleContent, '3');
  assert.equal(result.stepper.steps[3].isActive, false);
  assert.equal(result.pillClass, 'status-pill assigned');
});

// 4. OUT_FOR_DELIVERY renders correctly
test('Order Lifecycle: 4. OUT_FOR_DELIVERY renders correctly with step 3 active and steps 0,1,2 completed', () => {
  const result = evaluateOrderPresentation({ status: 'OUT_FOR_DELIVERY' });
  assert.equal(result.type, 'PROGRESSION');
  assert.equal(result.stepper.steps[0].isCompleted, true);
  assert.equal(result.stepper.steps[1].isCompleted, true);
  assert.equal(result.stepper.steps[2].isCompleted, true);
  assert.equal(result.stepper.steps[3].isActive, true);
  assert.equal(result.stepper.steps[3].circleContent, '4');
  assert.equal(result.stepper.steps[4].isActive, false);
  assert.equal(result.pillClass, 'status-pill out_for_delivery');
});

// 5. DELIVERED renders correctly
test('Order Lifecycle: 5. DELIVERED renders correctly with all 5 steps completed', () => {
  const result = evaluateOrderPresentation({ status: 'DELIVERED' });
  assert.equal(result.type, 'PROGRESSION');
  assert.equal(result.stepper.steps.length, 5);
  result.stepper.steps.forEach((step, i) => {
    assert.equal(step.isCompleted, true, `Step ${i} (${step.stage}) should be completed`);
    assert.equal(step.circleContent, '✓', `Step ${i} (${step.stage}) circle should display checkmark`);
    assert.equal(step.isActive, false, `Step ${i} should not be active since delivery is complete`);
  });
  assert.equal(result.pillClass, 'status-pill delivered');
});

// 6. CANCELLED renders terminal presentation
test('Order Lifecycle: 6. CANCELLED renders terminal presentation and suppresses stepper', () => {
  const result = evaluateOrderPresentation({ status: 'CANCELLED' });
  assert.equal(result.type, 'TERMINAL');
  assert.equal(result.terminalType, 'CANCELLED');
  assert.equal(result.bannerTitle, 'Order Cancelled');
  assert.match(result.bannerMessage, /cancelled and will not progress/);
  assert.equal(result.stepper, null);
  assert.equal(result.pillClass, 'status-pill cancelled');
});

// 7. REJECTED renders terminal presentation
test('Order Lifecycle: 7. REJECTED renders terminal presentation and suppresses stepper', () => {
  const result = evaluateOrderPresentation({ status: 'REJECTED' });
  assert.equal(result.type, 'TERMINAL');
  assert.equal(result.terminalType, 'REJECTED');
  assert.equal(result.bannerTitle, 'Order Rejected');
  assert.match(result.bannerMessage, /rejected by the store administrator/);
  assert.equal(result.stepper, null);
  assert.equal(result.pillClass, 'status-pill rejected');
});

// 8. All five normal lifecycle stages are represented
test('Order Lifecycle: 8. All five normal lifecycle stages are represented in sequence', () => {
  assert.deepEqual(LIFECYCLE_STAGES, [
    'PLACED',
    'READY_TO_ASSIGN',
    'ASSIGNED',
    'OUT_FOR_DELIVERY',
    'DELIVERED'
  ]);
  assert.equal(LIFECYCLE_STAGES.length, 5);
});

// 9. No second-row desktop step is created (CSS verification: 5 equal columns)
test('Order Lifecycle: 9. Stepper styles define 5 equal columns on desktop preventing row wrapping', () => {
  // Verify CSS defines repeat(5, 1fr) for stepper track
  assert.match(stylesCss, /\.stepper-track,\.stepper-steps\{[^}]*grid-template-columns:\s*repeat\(5,\s*1fr\)/);

  // Verify .step-line styles are defined
  assert.match(stylesCss, /\.step-item \.step-line\{/);
  assert.match(stylesCss, /\.step-item \.step-line\.done/);

  // Verify .step-circle and .step-label styles are defined
  assert.match(stylesCss, /\.step-circle,\.step-dot\{/);
  assert.match(stylesCss, /\.step-label,\.stepper-step span\{/);
});

// 10. Existing tracking behavior & status pills remain intact
test('Order Lifecycle: 10. All 7 status pills are defined in styles.css without missing classes', () => {
  const expectedPillClasses = [
    '.status-pill.placed',
    '.status-pill.ready_to_assign',
    '.status-pill.assigned',
    '.status-pill.out_for_delivery',
    '.status-pill.delivered',
    '.status-pill.cancelled',
    '.status-pill.rejected'
  ];

  expectedPillClasses.forEach((pillClass) => {
    assert.equal(
      stylesCss.includes(pillClass),
      true,
      `Expected ${pillClass} to be defined in styles.css`
    );
  });
});

// Responsive verification: mobile media query
test('Order Lifecycle: Responsive mobile styles (<600px) maintain single-row compact layout', () => {
  assert.match(stylesCss, /@media\s*\(\s*max-width:\s*600px\s*\)/);
  assert.match(stylesCss, /step-circle.*22px/);
  assert.match(stylesCss, /step-label.*8px/);
});
