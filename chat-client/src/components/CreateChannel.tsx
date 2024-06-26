import { Show, createSignal, useContext } from "solid-js";

import { useTheme } from "@suid/material";
import Box from "@suid/material/Box";
import Button from "@suid/material/Button";
import Modal from "@suid/material/Modal";
import TextField from "@suid/material/TextField";
import Typography from "@suid/material/Typography";

import { Channel, postChannel } from "../lib/chat-api-client";
import { AuthContext } from "./auth-context";

export default (props: { serverId: number }) => {
    const [newChannelName, setNewChannelName] = createSignal("");
    const [open, setOpen] = createSignal(false);
    const theme = useTheme();
    const [error, setError] = createSignal("");
    const [_, { addChannelToServer }] = useContext(AuthContext);

    return (
        <>
            <Button variant="outlined" onClick={() => setOpen(true)}>
                New Channel
            </Button>
            <Modal
                open={open()}
                onClose={() => setOpen(false)}
                aria-labelledby="create-server-modal-title"
            >
                <Box
                    component="form"
                    onSubmit={(e) => {
                        e.preventDefault();
                        if (newChannelName) {
                            postChannel(props.serverId, newChannelName())
                                .then((val) => {
                                    if (val) {
                                        console.debug(
                                            "New channel created: ",
                                            val
                                        );
                                        addChannelToServer(props.serverId, val);
                                        setOpen(false);
                                    }
                                    setError("Error making new server");
                                })
                                .catch((err) => {
                                    console.error(
                                        "Error making new server: ",
                                        err
                                    );
                                    setError(err);
                                });
                        }
                    }}
                    backgroundColor={theme.palette.background.paper}
                    position="absolute"
                    top="50%"
                    left="50%"
                    width={480}
                    sx={{ transform: "translate(-50%, -50%)" }}
                    border={`1px solid ${theme.palette.primary.light}`}
                    borderRadius={2}
                    padding={2}
                    displayRaw="flex"
                    flexDirection="column"
                    gap={1}
                >
                    <Typography variant="h6" component="h2">
                        Create a Channel
                    </Typography>
                    <TextField
                        onChange={(e) =>
                            setNewChannelName(e.currentTarget.value)
                        }
                        label="Name"
                    ></TextField>
                    <Button
                        variant="contained"
                        color="success"
                        type="submit"
                        disabled={!newChannelName()}
                    >
                        Create Channel
                    </Button>
                    <Show when={error()}>
                        <Typography color="error">{error()}</Typography>
                    </Show>
                </Box>
            </Modal>
        </>
    );
};
