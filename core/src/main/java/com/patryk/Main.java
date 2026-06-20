package com.patryk;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.math.collision.BoundingBox;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main extends ApplicationAdapter {
    private static final String PIECES_DIRECTORY = "figures/g3db";
    private static final String BOARD_DIRECTORY = "figures/g3db/chessboard";
    private static final String BLACK_SQUARES_MODEL = "Black Squares";
    private static final String WHITE_SQUARES_MODEL = "White Squares";
    private static final String BOTTOM_MODEL = "Bottom";
    private static final Color WHITE_PIECE_COLOR = new Color(0.92f, 0.9f, 0.86f, 1f);
    private static final Color BLACK_PIECE_COLOR = new Color(0.06f, 0.065f, 0.07f, 1f);
    private static final Color LIGHT_WOOD_COLOR = new Color(0.78f, 0.51f, 0.25f, 1f);
    private static final Color DARK_WOOD_COLOR = new Color(0.32f, 0.16f, 0.065f, 1f);
    private static final Color FRAME_WOOD_COLOR = new Color(0.48f, 0.27f, 0.12f, 1f);
    private static final Color BOTTOM_WOOD_COLOR = new Color(0.26f, 0.13f, 0.055f, 1f);
    private static final Color SELECTED_HIGHLIGHT_COLOR = new Color(1f, 0.82f, 0.08f, 0.72f);
    private static final Color MOVE_HIGHLIGHT_COLOR = new Color(0.12f, 0.95f, 0.42f, 0.58f);
    private static final float SQUARE_SIZE = 5f;
    private static final float HIGHLIGHT_SIZE = SQUARE_SIZE * 0.82f;
    private static final float HIGHLIGHT_THICKNESS = 0.08f;
    private static final float HIGHLIGHT_Y_OFFSET = 0.16f;
    private static final Pattern SQUARE_PATTERN = Pattern.compile("([a-h][1-8])$");

    public Environment environment;
    public PerspectiveCamera cam;
    public AssetManager assetManager;
    public Array<ModelInstance> instances = new Array<>();
    public Array<ModelInstance> pieceInstances = new Array<>();
    public Array<ModelInstance> highlightInstances = new Array<>();
    public Array<Model> generatedModels = new Array<>();
    public ModelBatch modelBatch;
    public CameraInputController camController;
    private Model selectedHighlightModel;
    private Model moveHighlightModel;
    private float boardTopY;
    private final Vector3 dragOffset = new Vector3();
    private final Vector3 dragIntersection = new Vector3();
    private final Vector3 piecePosition = new Vector3();
    private final Vector3 dragPlanePoint = new Vector3();
    private final Map<ModelInstance, ChessPiece> piecesByInstance = new HashMap<>();
    private final ChessPiece[][] board = new ChessPiece[8][8];
    private ModelInstance draggedPiece;
    private ChessPiece draggedPieceState;
    private ChessPiece clickSelectedPiece;
    private int draggedStartFile;
    private int draggedStartRank;
    private boolean pieceWasDragged;
    private PieceColor currentTurn = PieceColor.WHITE;
    private int enPassantFile = -1;
    private int enPassantRank = -1;
    private ChessPiece enPassantPawn;
    private boolean gameOver;

    @Override
    public void create() {
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));

        modelBatch = new ModelBatch();
        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(10f, 10f, 10f);
        cam.lookAt(0f, 0f, 0f);
        cam.near = 0.1f;
        cam.far = 300f;
        cam.update();

        camController = new CameraInputController(cam);

        assetManager = new AssetManager();
        loadModels(Gdx.files.internal(PIECES_DIRECTORY));
        loadModels(Gdx.files.internal(BOARD_DIRECTORY));
        assetManager.finishLoading();

        addModelInstances(Gdx.files.internal(BOARD_DIRECTORY));
        createHighlightModels();
        addModelInstances(Gdx.files.internal(PIECES_DIRECTORY));

        Gdx.input.setInputProcessor(new InputMultiplexer(createPieceInputAdapter(), camController));
    }

    @Override
    public void render() {
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        camController.update();

        modelBatch.begin(cam);
        modelBatch.render(instances, environment);
        modelBatch.render(highlightInstances, environment);
        modelBatch.end();
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        for (Model model : generatedModels) {
            model.dispose();
        }
        instances.clear();
        highlightInstances.clear();
        assetManager.dispose();
    }

    @Override
    public void resize(int width, int height) {
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    private void loadModels(FileHandle directory) {
        for (FileHandle file : directory.list(".g3db")) {
            assetManager.load(file.path(), Model.class);
        }
    }

    private void addModelInstances(FileHandle directory) {
        for (FileHandle file : directory.list(".g3db")) {
            Model loadedModel = assetManager.get(file.path(), Model.class);
            String modelName = file.nameWithoutExtension();

            applyMaterialDefaults(loadedModel, modelName);

            ModelInstance modelInstance = new ModelInstance(loadedModel);
            if (isBoardModel(directory, modelName)) {
                updateBoardTop(modelInstance);
            } else {
                ChessPiece piece = createPiece(modelInstance, modelName);
                placePiece(piece, piece.file, piece.rank);
                positionPieceOnBoard(piece);
                pieceInstances.add(modelInstance);
            }
            instances.add(modelInstance);
        }
    }

    private void applyMaterialDefaults(Model model, String modelName) {
        for (Material material : model.materials) {
            if (BLACK_SQUARES_MODEL.equals(modelName)) {
                material.set(ColorAttribute.createDiffuse(DARK_WOOD_COLOR));
            } else if (WHITE_SQUARES_MODEL.equals(modelName)) {
                material.set(ColorAttribute.createDiffuse(LIGHT_WOOD_COLOR));
            } else if (BOTTOM_MODEL.equals(modelName)) {
                material.set(ColorAttribute.createDiffuse(BOTTOM_WOOD_COLOR));
            } else if (isBoardFrameModel(modelName)) {
                material.set(ColorAttribute.createDiffuse(FRAME_WOOD_COLOR));
            } else if (isPieceModel(modelName)) {
                material.set(ColorAttribute.createDiffuse(isBlackPiece(modelName) ? BLACK_PIECE_COLOR : WHITE_PIECE_COLOR));
            } else if (!material.has(ColorAttribute.Diffuse)) {
                material.set(ColorAttribute.createDiffuse(Color.WHITE));
            }
        }
    }

    private boolean isBoardFrameModel(String modelName) {
        return "Inner Frame".equals(modelName) || "Outer Frame".equals(modelName);
    }

    private boolean isPieceModel(String modelName) {
        return SQUARE_PATTERN.matcher(modelName).find();
    }

    private boolean isBlackPiece(String modelName) {
        Matcher matcher = SQUARE_PATTERN.matcher(modelName);
        return matcher.find() && matcher.group(1).charAt(1) >= '7';
    }

    private boolean isBoardModel(FileHandle directory, String modelName) {
        return directory.path().equals(Gdx.files.internal(BOARD_DIRECTORY).path())
            || BLACK_SQUARES_MODEL.equals(modelName);
    }

    private void createHighlightModels() {
        ModelBuilder modelBuilder = new ModelBuilder();
        selectedHighlightModel = modelBuilder.createBox(HIGHLIGHT_SIZE, HIGHLIGHT_THICKNESS, HIGHLIGHT_SIZE,
            createHighlightMaterial(SELECTED_HIGHLIGHT_COLOR),
            Usage.Position | Usage.Normal);
        moveHighlightModel = modelBuilder.createBox(HIGHLIGHT_SIZE, HIGHLIGHT_THICKNESS, HIGHLIGHT_SIZE,
            createHighlightMaterial(MOVE_HIGHLIGHT_COLOR),
            Usage.Position | Usage.Normal);
        generatedModels.add(selectedHighlightModel);
        generatedModels.add(moveHighlightModel);
    }

    private Material createHighlightMaterial(Color color) {
        return new Material(
            ColorAttribute.createDiffuse(color),
            new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, color.a)
        );
    }

    private void showHighlightsFor(ChessPiece piece) {
        addHighlight(selectedHighlightModel, piece.file, piece.rank);
        for (int file = 0; file < 8; file++) {
            for (int rank = 1; rank <= 8; rank++) {
                if (isLegalMove(piece, file, rank)) {
                    addHighlight(moveHighlightModel, file, rank);
                }
            }
        }
    }

    private void addHighlight(Model model, int file, int rank) {
        ModelInstance highlight = new ModelInstance(model);
        highlight.transform.setTranslation(
            (file - 3.5f) * SQUARE_SIZE,
            boardTopY + HIGHLIGHT_Y_OFFSET,
            (4.5f - rank) * SQUARE_SIZE
        );
        highlightInstances.add(highlight);
    }

    private void clearHighlights() {
        highlightInstances.clear();
    }

    private ChessPiece createPiece(ModelInstance modelInstance, String modelName) {
        Matcher matcher = SQUARE_PATTERN.matcher(modelName);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Piece model name must end with a square: " + modelName);
        }

        String square = matcher.group(1);
        int fileIndex = square.charAt(0) - 'a';
        int rank = square.charAt(1) - '0';
        PieceType type = parsePieceType(modelName);
        PieceColor color = rank <= 2 ? PieceColor.WHITE : PieceColor.BLACK;

        ChessPiece piece = new ChessPiece(modelInstance, type, color, fileIndex, rank);
        piecesByInstance.put(modelInstance, piece);
        return piece;
    }

    private PieceType parsePieceType(String modelName) {
        if (modelName.startsWith("Pawn")) {
            return PieceType.PAWN;
        }
        if (modelName.startsWith("Knight")) {
            return PieceType.KNIGHT;
        }
        if (modelName.startsWith("Bishop")) {
            return PieceType.BISHOP;
        }
        if (modelName.startsWith("Rook")) {
            return PieceType.ROOK;
        }
        if (modelName.startsWith("Queen")) {
            return PieceType.QUEEN;
        }
        if (modelName.startsWith("King")) {
            return PieceType.KING;
        }
        throw new IllegalArgumentException("Unknown piece type: " + modelName);
    }

    private void updateBoardTop(ModelInstance modelInstance) {
        boardTopY = Math.max(boardTopY, getBounds(modelInstance).max.y);
    }

    private BoundingBox getBounds(ModelInstance modelInstance) {
        BoundingBox bounds = new BoundingBox();
        modelInstance.calculateBoundingBox(bounds);
        bounds.mul(modelInstance.transform);
        return bounds;
    }

    private InputAdapter createPieceInputAdapter() {
        return new InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if (button != Input.Buttons.LEFT) {
                    return false;
                }

                ModelInstance selectedPiece = findPieceAt(screenX, screenY);
                if (clickSelectedPiece != null && canUseClickMove(selectedPiece)) {
                    if (!intersectBoardPlane(screenX, screenY, dragIntersection)) {
                        return false;
                    }

                    int[] targetSquare = getNearestSquare(dragIntersection.x, dragIntersection.z);
                    if (targetSquare != null && tryMovePiece(clickSelectedPiece, targetSquare[0], targetSquare[1])) {
                        clearClickSelection();
                        return true;
                    }
                }

                if (selectedPiece == null) {
                    return false;
                }
                ChessPiece selectedPieceState = piecesByInstance.get(selectedPiece);
                if (gameOver || selectedPieceState == null || selectedPieceState.color != currentTurn) {
                    return false;
                }

                if (!intersectBoardPlane(screenX, screenY, dragIntersection)) {
                    return false;
                }

                draggedPiece = selectedPiece;
                draggedPieceState = selectedPieceState;
                draggedStartFile = selectedPieceState.file;
                draggedStartRank = selectedPieceState.rank;
                pieceWasDragged = false;
                selectPieceForClick(selectedPieceState);
                showHighlightsFor(selectedPieceState);
                draggedPiece.transform.getTranslation(piecePosition);
                dragOffset.set(piecePosition.x - dragIntersection.x, 0f, piecePosition.z - dragIntersection.z);
                camController.autoUpdate = false;
                return true;
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                if (draggedPiece == null || !intersectBoardPlane(screenX, screenY, dragIntersection)) {
                    return false;
                }

                float targetX = dragIntersection.x + dragOffset.x;
                float targetZ = dragIntersection.z + dragOffset.z;
                draggedPiece.transform.setTranslation(targetX, getPieceBoardY(draggedPiece), targetZ);
                pieceWasDragged = true;
                return true;
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                if (button != Input.Buttons.LEFT || draggedPiece == null) {
                    return false;
                }

                if (pieceWasDragged) {
                    int[] targetSquare = getNearestSquare(draggedPiece);
                    if (targetSquare == null || !tryMovePiece(draggedPieceState, targetSquare[0], targetSquare[1])) {
                        placePiece(draggedPieceState, draggedStartFile, draggedStartRank);
                        positionPieceOnBoard(draggedPieceState);
                    }
                    clearClickSelection();
                } else {
                    positionPieceOnBoard(draggedPieceState);
                }
                draggedPiece = null;
                draggedPieceState = null;
                camController.autoUpdate = true;
                return true;
            }
        };
    }

    private ModelInstance findPieceAt(int screenX, int screenY) {
        Ray pickRay = cam.getPickRay(screenX, screenY);
        ModelInstance closestPiece = null;
        float closestDistance = Float.MAX_VALUE;

        for (ModelInstance piece : pieceInstances) {
            BoundingBox bounds = getBounds(piece);
            if (!Intersector.intersectRayBoundsFast(pickRay, bounds)) {
                continue;
            }

            bounds.getCenter(piecePosition);
            float distance = pickRay.origin.dst2(piecePosition);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestPiece = piece;
            }
        }

        return closestPiece;
    }

    private boolean intersectBoardPlane(int screenX, int screenY, Vector3 intersection) {
        Ray pickRay = cam.getPickRay(screenX, screenY);
        dragPlanePoint.set(0f, boardTopY, 0f);
        return Intersector.intersectRayPlane(pickRay, new com.badlogic.gdx.math.Plane(Vector3.Y, dragPlanePoint), intersection);
    }

    private float getPieceBoardY(ModelInstance piece) {
        BoundingBox localBounds = new BoundingBox();
        piece.calculateBoundingBox(localBounds);
        return boardTopY - localBounds.min.y;
    }

    private int[] getNearestSquare(ModelInstance piece) {
        piece.transform.getTranslation(piecePosition);
        return getNearestSquare(piecePosition.x, piecePosition.z);
    }

    private int[] getNearestSquare(float x, float z) {
        int file = Math.round(x / SQUARE_SIZE + 3.5f);
        int rank = Math.round(4.5f - z / SQUARE_SIZE);
        if (!isInsideBoard(file, rank)) {
            return null;
        }
        return new int[] {file, rank};
    }

    private boolean canUseClickMove(ModelInstance clickedPiece) {
        if (clickedPiece == null) {
            return true;
        }

        ChessPiece clickedPieceState = piecesByInstance.get(clickedPiece);
        return clickedPieceState != null && clickedPieceState.color != currentTurn;
    }

    private void selectPieceForClick(ChessPiece piece) {
        clickSelectedPiece = piece;
        clearHighlights();
    }

    private void clearClickSelection() {
        clickSelectedPiece = null;
        clearHighlights();
    }

    private void placePiece(ChessPiece piece, int file, int rank) {
        if (isInsideBoard(piece.file, piece.rank) && board[piece.file][piece.rank - 1] == piece) {
            board[piece.file][piece.rank - 1] = null;
        }
        piece.file = file;
        piece.rank = rank;
        board[file][rank - 1] = piece;
    }

    private void positionPieceOnBoard(ChessPiece piece) {
        float x = (piece.file - 3.5f) * SQUARE_SIZE;
        float z = (4.5f - piece.rank) * SQUARE_SIZE;
        piece.instance.transform.setTranslation(x, getPieceBoardY(piece.instance), z);
    }

    private boolean tryMovePiece(ChessPiece piece, int targetFile, int targetRank) {
        int startFile = piece.file;
        int startRank = piece.rank;
        if (!isLegalMove(piece, targetFile, targetRank)) {
            return false;
        }

        if (piece.type == PieceType.KING && Math.abs(targetFile - startFile) == 2) {
            performCastling(piece, targetFile);
        } else {
            boolean enPassantCapture = isEnPassantCapture(piece, targetFile, targetRank);
            ChessPiece capturedPiece = enPassantCapture ? enPassantPawn : board[targetFile][targetRank - 1];
            if (capturedPiece != null) {
                removePiece(capturedPiece);
            }
            placePiece(piece, targetFile, targetRank);
            piece.hasMoved = true;
            if (piece.type == PieceType.PAWN && (targetRank == 1 || targetRank == 8)) {
                piece.type = PieceType.QUEEN;
            }
            updateEnPassantState(piece, startFile, startRank, targetRank);
        }

        positionPieceOnBoard(piece);
        currentTurn = currentTurn == PieceColor.WHITE ? PieceColor.BLACK : PieceColor.WHITE;
        updateGameOverState();
        return true;
    }

    private boolean isLegalMove(ChessPiece piece, int targetFile, int targetRank) {
        if (!isInsideBoard(targetFile, targetRank) || (piece.file == targetFile && piece.rank == targetRank)) {
            return false;
        }

        ChessPiece targetPiece = board[targetFile][targetRank - 1];
        if (targetPiece != null && targetPiece.color == piece.color) {
            return false;
        }
        if (targetPiece != null && targetPiece.type == PieceType.KING) {
            return false;
        }

        boolean followsPieceRules;
        switch (piece.type) {
            case PAWN:
                followsPieceRules = isLegalPawnMove(piece, targetFile, targetRank);
                break;
            case KNIGHT:
                followsPieceRules = isLegalKnightMove(piece, targetFile, targetRank);
                break;
            case BISHOP:
                followsPieceRules = isLegalSlidingMove(piece, targetFile, targetRank, true, false);
                break;
            case ROOK:
                followsPieceRules = isLegalSlidingMove(piece, targetFile, targetRank, false, true);
                break;
            case QUEEN:
                followsPieceRules = isLegalSlidingMove(piece, targetFile, targetRank, true, true);
                break;
            case KING:
                followsPieceRules = isLegalKingMove(piece, targetFile, targetRank);
                break;
            default:
                followsPieceRules = false;
        }

        return followsPieceRules && !wouldLeaveKingInCheck(piece, targetFile, targetRank);
    }

    private boolean isLegalPawnMove(ChessPiece piece, int targetFile, int targetRank) {
        int direction = piece.color == PieceColor.WHITE ? 1 : -1;
        int fileDelta = targetFile - piece.file;
        int rankDelta = targetRank - piece.rank;
        ChessPiece targetPiece = board[targetFile][targetRank - 1];

        if (fileDelta == 0 && rankDelta == direction && targetPiece == null) {
            return true;
        }
        if (fileDelta == 0 && rankDelta == 2 * direction && !piece.hasMoved && targetPiece == null) {
            return board[piece.file][piece.rank - 1 + direction] == null;
        }
        if (Math.abs(fileDelta) == 1 && rankDelta == direction) {
            return targetPiece != null && targetPiece.color != piece.color
                || isEnPassantCapture(piece, targetFile, targetRank);
        }
        return false;
    }

    private boolean isLegalKnightMove(ChessPiece piece, int targetFile, int targetRank) {
        int fileDelta = Math.abs(targetFile - piece.file);
        int rankDelta = Math.abs(targetRank - piece.rank);
        return fileDelta * rankDelta == 2;
    }

    private boolean isLegalSlidingMove(ChessPiece piece, int targetFile, int targetRank, boolean diagonal, boolean straight) {
        int fileDelta = targetFile - piece.file;
        int rankDelta = targetRank - piece.rank;
        boolean movesDiagonally = Math.abs(fileDelta) == Math.abs(rankDelta);
        boolean movesStraight = fileDelta == 0 || rankDelta == 0;
        if ((!diagonal || !movesDiagonally) && (!straight || !movesStraight)) {
            return false;
        }
        return isPathClear(piece.file, piece.rank, targetFile, targetRank);
    }

    private boolean isLegalKingMove(ChessPiece piece, int targetFile, int targetRank) {
        int fileDelta = Math.abs(targetFile - piece.file);
        int rankDelta = Math.abs(targetRank - piece.rank);
        if (fileDelta <= 1 && rankDelta <= 1) {
            return !isSquareAttacked(targetFile, targetRank, opposite(piece.color));
        }
        return rankDelta == 0 && fileDelta == 2 && canCastle(piece, targetFile);
    }

    private boolean canCastle(ChessPiece king, int targetFile) {
        if (king.hasMoved || isKingInCheck(king.color)) {
            return false;
        }

        int rookFile = targetFile > king.file ? 7 : 0;
        int direction = targetFile > king.file ? 1 : -1;
        ChessPiece rook = board[rookFile][king.rank - 1];
        if (rook == null || rook.type != PieceType.ROOK || rook.color != king.color || rook.hasMoved) {
            return false;
        }
        if (!isPathClear(king.file, king.rank, rookFile, king.rank)) {
            return false;
        }
        return !isSquareAttacked(king.file + direction, king.rank, opposite(king.color))
            && !isSquareAttacked(king.file + 2 * direction, king.rank, opposite(king.color));
    }

    private void performCastling(ChessPiece king, int targetFile) {
        int direction = targetFile > king.file ? 1 : -1;
        ChessPiece rook = board[targetFile > king.file ? 7 : 0][king.rank - 1];
        clearEnPassantState();
        placePiece(king, targetFile, king.rank);
        king.hasMoved = true;
        placePiece(rook, targetFile - direction, rook.rank);
        rook.hasMoved = true;
        positionPieceOnBoard(rook);
    }

    private boolean wouldLeaveKingInCheck(ChessPiece piece, int targetFile, int targetRank) {
        boolean enPassantCapture = isEnPassantCapture(piece, targetFile, targetRank);
        ChessPiece capturedPiece = enPassantCapture ? enPassantPawn : board[targetFile][targetRank - 1];
        int startFile = piece.file;
        int startRank = piece.rank;

        board[startFile][startRank - 1] = null;
        if (capturedPiece != null) {
            board[capturedPiece.file][capturedPiece.rank - 1] = null;
        }
        piece.file = targetFile;
        piece.rank = targetRank;
        board[targetFile][targetRank - 1] = piece;

        boolean kingInCheck = isKingInCheck(piece.color);

        board[targetFile][targetRank - 1] = null;
        piece.file = startFile;
        piece.rank = startRank;
        board[startFile][startRank - 1] = piece;
        if (capturedPiece != null) {
            board[capturedPiece.file][capturedPiece.rank - 1] = capturedPiece;
        }

        return kingInCheck;
    }

    private boolean isKingInCheck(PieceColor color) {
        ChessPiece king = null;
        for (ChessPiece piece : piecesByInstance.values()) {
            if (piece.type == PieceType.KING && piece.color == color && piece.isActive) {
                king = piece;
                break;
            }
        }
        return king != null && isSquareAttacked(king.file, king.rank, opposite(color));
    }

    private void updateGameOverState() {
        if (hasAnyLegalMove(currentTurn)) {
            return;
        }

        gameOver = true;
        if (isKingInCheck(currentTurn)) {
            Gdx.app.log("Chess", "Checkmate. Winner: " + opposite(currentTurn));
        } else {
            Gdx.app.log("Chess", "Stalemate.");
        }
    }

    private boolean hasAnyLegalMove(PieceColor color) {
        for (ChessPiece piece : piecesByInstance.values()) {
            if (!piece.isActive || piece.color != color) {
                continue;
            }
            for (int file = 0; file < 8; file++) {
                for (int rank = 1; rank <= 8; rank++) {
                    if (isLegalMove(piece, file, rank)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isSquareAttacked(int file, int rank, PieceColor attackerColor) {
        for (ChessPiece piece : piecesByInstance.values()) {
            if (!piece.isActive || piece.color != attackerColor) {
                continue;
            }
            if (attacksSquare(piece, file, rank)) {
                return true;
            }
        }
        return false;
    }

    private boolean attacksSquare(ChessPiece piece, int file, int rank) {
        int fileDelta = file - piece.file;
        int rankDelta = rank - piece.rank;
        switch (piece.type) {
            case PAWN:
                return rankDelta == (piece.color == PieceColor.WHITE ? 1 : -1) && Math.abs(fileDelta) == 1;
            case KNIGHT:
                return Math.abs(fileDelta) * Math.abs(rankDelta) == 2;
            case BISHOP:
                return Math.abs(fileDelta) == Math.abs(rankDelta) && isPathClear(piece.file, piece.rank, file, rank);
            case ROOK:
                return (fileDelta == 0 || rankDelta == 0) && isPathClear(piece.file, piece.rank, file, rank);
            case QUEEN:
                return (Math.abs(fileDelta) == Math.abs(rankDelta) || fileDelta == 0 || rankDelta == 0)
                    && isPathClear(piece.file, piece.rank, file, rank);
            case KING:
                return Math.abs(fileDelta) <= 1 && Math.abs(rankDelta) <= 1;
            default:
                return false;
        }
    }

    private boolean isPathClear(int startFile, int startRank, int targetFile, int targetRank) {
        int fileStep = Integer.compare(targetFile, startFile);
        int rankStep = Integer.compare(targetRank, startRank);
        int file = startFile + fileStep;
        int rank = startRank + rankStep;
        while (file != targetFile || rank != targetRank) {
            if (board[file][rank - 1] != null) {
                return false;
            }
            file += fileStep;
            rank += rankStep;
        }
        return true;
    }

    private boolean isEnPassantCapture(ChessPiece piece, int targetFile, int targetRank) {
        return piece.type == PieceType.PAWN
            && targetFile == enPassantFile
            && targetRank == enPassantRank
            && enPassantPawn != null
            && enPassantPawn.color != piece.color
            && board[targetFile][targetRank - 1] == null;
    }

    private void updateEnPassantState(ChessPiece piece, int startFile, int startRank, int targetRank) {
        if (piece.type == PieceType.PAWN && Math.abs(targetRank - startRank) == 2) {
            enPassantFile = startFile;
            enPassantRank = (startRank + targetRank) / 2;
            enPassantPawn = piece;
        } else {
            clearEnPassantState();
        }
    }

    private void clearEnPassantState() {
        enPassantFile = -1;
        enPassantRank = -1;
        enPassantPawn = null;
    }

    private void removePiece(ChessPiece piece) {
        piece.isActive = false;
        board[piece.file][piece.rank - 1] = null;
        pieceInstances.removeValue(piece.instance, true);
        instances.removeValue(piece.instance, true);
    }

    private boolean isInsideBoard(int file, int rank) {
        return file >= 0 && file < 8 && rank >= 1 && rank <= 8;
    }

    private PieceColor opposite(PieceColor color) {
        return color == PieceColor.WHITE ? PieceColor.BLACK : PieceColor.WHITE;
    }

    private enum PieceColor {
        WHITE,
        BLACK
    }

    private enum PieceType {
        PAWN,
        KNIGHT,
        BISHOP,
        ROOK,
        QUEEN,
        KING
    }

    private static class ChessPiece {
        private final ModelInstance instance;
        private PieceType type;
        private final PieceColor color;
        private int file;
        private int rank;
        private boolean hasMoved;
        private boolean isActive = true;

        private ChessPiece(ModelInstance instance, PieceType type, PieceColor color, int file, int rank) {
            this.instance = instance;
            this.type = type;
            this.color = color;
            this.file = file;
            this.rank = rank;
        }
    }
}
