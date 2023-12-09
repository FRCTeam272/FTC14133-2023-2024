package org.firstinspires.ftc.teamcode;

import com.acmerobotics.roadrunner.geometry.Pose2d;
import com.acmerobotics.roadrunner.geometry.Vector2d;
import com.acmerobotics.roadrunner.trajectory.Trajectory;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.util.ElapsedTime;

import org.firstinspires.ftc.teamcode.Roadrunner.drive.SampleMecanumDrive;
import org.firstinspires.ftc.teamcode.Roadrunner.trajectorysequence.TrajectorySequence;
import org.firstinspires.ftc.teamcode.Subsytem.Arm;
import org.firstinspires.ftc.teamcode.Subsytem.Intake;

@com.qualcomm.robotcore.eventloop.opmode.Autonomous(group = "StateTest")
public class Autonomous extends LinearOpMode {

    static final String SPIKE_CENTER = "center";
    static final String SPIKE_LEFT = "left";
    static final String SPIKE_RIGHT = "right";

    static final String SIDE_FAR = "far";
    static final String SIDE_CLOSE = "close";

    static final String ALLIANCE_RED = "red";
    static final String ALLIANCE_BLUE = "blue";



    enum State {
        SPIKE,
        BACKDROP,
        ALIGN,
        PLACE,
        PARK,
        IDLE
    }

    State currentState = State.IDLE;

    int armSlidePos = -1;

    private Arm arm=null;
    private Intake intake=null;

    @Override
    public void runOpMode() throws InterruptedException {
        // Creating Drivetrain
        SampleMecanumDrive drive = new SampleMecanumDrive(hardwareMap);

        arm = new Arm(hardwareMap);
        intake = new Intake(hardwareMap);

        String[] selectedArray = autoSelector();

        String alliance = selectedArray[0];
        String spike = selectedArray[1];
        if (alliance.equals(ALLIANCE_BLUE) && !(spike.equals(SPIKE_CENTER))){
            if (spike.equals(SPIKE_LEFT)){
                spike = SPIKE_RIGHT;
            }else if (spike.equals(SPIKE_RIGHT)){
                spike = SPIKE_LEFT;
            }
        }
        String side = selectedArray[2];

        double startY = 0, startX = 0;

        if (side.equals(SIDE_FAR) && alliance.equals(ALLIANCE_RED)){
            startX = -35;
            startY = -64;
        }else if (side.equals(SIDE_CLOSE) && alliance.equals(ALLIANCE_RED)){
            startX = 12;
            startY = -64;
        }else if (side.equals(SIDE_FAR) && alliance.equals(ALLIANCE_BLUE)){
            startX = -35;
            startY = 64;
        }else if (side.equals(SIDE_CLOSE) && alliance.equals(ALLIANCE_BLUE)){
            startX = 12;
            startY = 64;
        }

        Pose2d startPose = new Pose2d(startX, startY, Math.toRadians(0));
        drive.setPoseEstimate(startPose);


        double sideOffset = 0;
        if (side.equals(SIDE_CLOSE)){
            sideOffset = 47;
        }

        int allianceFlip = 1;
        if (alliance.equals(ALLIANCE_BLUE)){
            allianceFlip = -1;
        }



        Trajectory spikeL = drive.trajectoryBuilder(startPose)
                .lineToConstantHeading(new Vector2d((-47+sideOffset), (-42*allianceFlip)))
                .build();
        Trajectory spikeC = drive.trajectoryBuilder(startPose)
                .lineToConstantHeading(new Vector2d((-36+sideOffset), (-36*allianceFlip)))
                .build();
        Trajectory spikeR = drive.trajectoryBuilder(startPose)
                .lineToConstantHeading(new Vector2d((-24+sideOffset), (-42*allianceFlip)))
                .build();



        currentState = State.SPIKE;
        switch (spike){
            case SPIKE_LEFT:
                drive.followTrajectoryAsync(spikeL);
                break;
            case SPIKE_RIGHT:
                drive.followTrajectoryAsync(spikeR);
                break;
            case SPIKE_CENTER:
                drive.followTrajectoryAsync(spikeC);
                break;
        }

        ElapsedTime intakeTimer = new ElapsedTime();
        ElapsedTime armTimer = new ElapsedTime();
        boolean resetArmTimer = true;
        int outtakeState = 0;

        while (opModeIsActive() && !isStopRequested()) {
            switch (currentState){
                case SPIKE:
                    if (!drive.isBusy()){
                        currentState = State.BACKDROP;

                        //armSlidePos = 1;

                        switch (side){
                            case SIDE_CLOSE:
                                drive.followTrajectorySequenceAsync(updateStraightTo(drive, allianceFlip, sideOffset));
                                break;
                            case SIDE_FAR:
                                switch (spike){
                                    case SPIKE_LEFT: case SPIKE_RIGHT:
                                        drive.followTrajectorySequenceAsync(updatefarLR(drive, allianceFlip));
                                        break;
                                    case SPIKE_CENTER:
                                        drive.followTrajectorySequenceAsync(updateStraightTo(drive, allianceFlip, sideOffset));
                                        break;
                                }
                        }
                    }
                    break;

                case BACKDROP:
                    if (!drive.isBusy()){
                        currentState = State.ALIGN;

                        double yBack = 0;
                        switch (spike) {
                            case SPIKE_LEFT:
                                yBack = -30;
                                break;
                            case SPIKE_RIGHT:
                                yBack = -42;
                                break;
                            case SPIKE_CENTER:
                                yBack = -36;
                                break;
                        }

                        if (alliance.equals(ALLIANCE_BLUE)){
                            yBack += 72;
                        }

                        drive.followTrajectoryAsync(updateBackAlign(drive, yBack));
                    }
                    break;

                case ALIGN:
                    if (!drive.isBusy() && resetArmTimer){
                        resetArmTimer = false;
                        armTimer.reset();
                    }
                    if (armTimer.seconds() >= 7.5 && !resetArmTimer){
                        currentState = State.PLACE;

                        outtakeState = 1;
                        intakeTimer.reset();

                    }
                    break;

                case PLACE:
                    if (intakeTimer.seconds() >= 2.0){
                        outtakeState = 0;

                        currentState = State.PARK;
                        drive.followTrajectoryAsync(updatePark(drive, allianceFlip));
                    }
                    break;

                case PARK:
                    if (!drive.isBusy()){
                        currentState = State.IDLE;
                    }
                    break;

                case IDLE:
                    break;
            }

            drive.update();

            arm.GoToPosition(armSlidePos, intake, telemetry);
            intake.objpivot.updateIntakeAngle(arm, telemetry);
            intake.objcatcher.runOutake(outtakeState);


        }

    }

    public TrajectorySequence updateStraightTo(SampleMecanumDrive drive, int allianceFlip, double sideOffset){

        telemetry.addData("poseEstimate", drive.getPoseEstimate());
        telemetry.update();

        return drive.trajectorySequenceBuilder(drive.getPoseEstimate())
                .lineToConstantHeading(new Vector2d((-36+sideOffset), (-39*allianceFlip)))
                .lineToConstantHeading(new Vector2d(40, (-36*allianceFlip)))
                .build();
    }

    public TrajectorySequence updatefarLR(SampleMecanumDrive drive, int allianceFlip){

        telemetry.addData("poseEstimate", drive.getPoseEstimate());
        telemetry.update();

        return drive.trajectorySequenceBuilder(drive.getPoseEstimate())

                .addSpatialMarker(new Vector2d(9, -11*(allianceFlip)), () -> {
                    armSlidePos = 1;
                })

                .lineToConstantHeading(new Vector2d(-36, (-48*allianceFlip)))
                .lineToConstantHeading(new Vector2d(-33, (-11*allianceFlip)))
                .lineToConstantHeading(new Vector2d(35, (-11*allianceFlip)))
                .splineToConstantHeading(new Vector2d(40, (-36*allianceFlip)), Math.toRadians(180))
                .build();
    }

    public Trajectory updateBackAlign(SampleMecanumDrive drive, double yValue){

        telemetry.addData("poseEstimate", drive.getPoseEstimate());
        telemetry.update();

        return drive.trajectoryBuilder(drive.getPoseEstimate())
                .lineToConstantHeading(new Vector2d(40, yValue))
                .build();
    }

    public Trajectory updatePark(SampleMecanumDrive drive, int allianceFlip){

        telemetry.addData("poseEstimate", drive.getPoseEstimate());
        telemetry.update();

        return drive.trajectoryBuilder(drive.getPoseEstimate())
                .splineToConstantHeading(new Vector2d(59, (-64*allianceFlip)), Math.toRadians(0))
                .build();
    }

    public String[] autoSelector(){
        // Auto Selector
        String alliance = ALLIANCE_BLUE;
        String spike = SPIKE_LEFT;
        String side = SIDE_CLOSE;

        while (!opModeIsActive() && !isStopRequested()){
            if (gamepad1.x){
                alliance = ALLIANCE_BLUE;
            }else if (gamepad1.b){
                alliance = ALLIANCE_RED;
            }
            telemetry.addData("Select Alliance (Gamepad1 X = Blue, Gamepad1 B = Red)", "");
            telemetry.addData("Current Alliance Selected : ", alliance.toUpperCase());
            telemetry.addData("", "");

            if (gamepad1.dpad_left){
                spike = SPIKE_LEFT;
            }else if (gamepad1.dpad_right){
                spike = SPIKE_RIGHT;
            }else if (gamepad1.dpad_up){
                spike = SPIKE_CENTER;
            }
            telemetry.addData("Select Spike Mark (Gamepad1 D-PAD Left = Left Spike, Gamepad1 D-PAD Up = Center Spike, Gamepad1 D-PAD Right = Right Spike)", "");
            telemetry.addData("Current Spike Mark Selected : ", spike.toUpperCase());
            telemetry.addData("", "");

            if (gamepad1.y){
                side = SIDE_FAR;
            }else if (gamepad1.a){
                side = SIDE_CLOSE;
            }
            telemetry.addData("Select Side (Gamepad1 Y = Far, Gamepad1 A = Close)", "");
            telemetry.addData("Current Side Selected : ", side.toUpperCase());
            telemetry.addData("", "");

            telemetry.update();
        }

        return new String[] {alliance, spike, side};

    }

}