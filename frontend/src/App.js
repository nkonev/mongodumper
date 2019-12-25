import React, {useState, useEffect} from 'react';
import './App.css';
import axios from 'axios'
import Modal from '@material-ui/core/Modal';
import {makeStyles} from '@material-ui/core/styles';
import List from '@material-ui/core/List';
import ListItem from '@material-ui/core/ListItem';
import ListItemText from '@material-ui/core/ListItemText';
import Button from '@material-ui/core/Button';
import Grid from '@material-ui/core/Grid';
import TextField from '@material-ui/core/TextField';
import Fab from '@material-ui/core/Fab';
import AddIcon from '@material-ui/icons/Add';
import Fade from '@material-ui/core/Fade';
import Box from '@material-ui/core/Box';

const useStyles = makeStyles(theme => ({
    root: {
        width: '100%',
        backgroundColor: theme.palette.background.paper,
    },
    fabButton: {
        position: 'absolute',
        zIndex: 1,
        bottom: 30,
        right: 30,
        margin: '0 auto',
    },
    paper: {
        position: 'absolute',
        width: 400,
        backgroundColor: theme.palette.background.paper,
        border: '2px solid #000',
        boxShadow: theme.shadows[5],
        padding: theme.spacing(2),
    },
    confirm: {
        position: 'absolute',
        backgroundColor: theme.palette.background.paper,
        border: '2px solid #000',
        boxShadow: theme.shadows[5],
        padding: theme.spacing(2),
    },
}));

function getModalStyle() {
    const top = 50;
    const left = 50;

    return {
        top: `${top}%`,
        left: `${left}%`,
        transform: `translate(-${top}%, -${left}%)`,
    };
}

function App() {
    // state
    const [connections, setConnections] = useState([]);

    const fetchData = () => {
        console.log("before get");
        axios.get(`db`)
            .then(message => {
                const m = message.data;
                setConnections(m);
            });
    };

    useEffect(() => {
        fetchData();
    }, []);

    const classes = useStyles();

    const onDelete = id => {
        console.log("Deleting", id);
        axios.delete(`db/${id}`)
            .then(() => {
                fetchData();
            });
    };

    const onSave = (c) => {
        (c.id ? axios.put(`db`, c) : axios.post(`db`, c))
            .then(() => {
                fetchData();
                handleCloseEditModal();
            });
    };

    // getModalStyle is not a pure function, we roll the style only on the first render
    const [modalStyle] = React.useState(getModalStyle);
    const [openEditModal, setOpenEditModal] = React.useState(false);
    const [editDto, setEditDto] = React.useState({});
    const [valid, setValid] = React.useState(true);
    const [openConfirmModal, setOpenConfirmModal] = React.useState(false);
    const [dbToDelete, setDbToDelete] = React.useState({});

    const handleOpen = (c) => {
        console.log("Editing modal", c.id);
        setEditDto(c);
        validate(c);
        setOpenEditModal(true);
    };

    const handleCloseEditModal = () => {
        setOpenEditModal(false);
    };

    const handleCloseConfirmModal = () => {
        setOpenConfirmModal(false);
    };

    const validString = s => {
        if (s) {
            return true
        } else {
            return false
        }
    };

    const validate = (dto) => {
        let v = validString(dto.name) && validString(dto.connectionUrl);
        console.log("Valid? " + JSON.stringify(dto) + " : " + v);
        setValid(v)
    };

    const handleChangeName = event => {
        const dto = {...editDto, name: event.target.value};
        setEditDto(dto);
        validate(dto);
    };

    const handleChangeConnectionUrl = event => {
        const dto = {...editDto, connectionUrl: event.target.value};
        setEditDto(dto);
        validate(dto);
    };

    const handleDump = id => {
        const d = "dump/" + id;
        console.log(`Will open ${window.location.href + d} for download gzipped file`);
        window.open(d, '_blank');
    };

    const openDeleteModal = (dto) => {
        setDbToDelete(dto);
        setOpenConfirmModal(true);
    };

    const handleDelete = (id) => {
        onDelete(id);
        handleCloseConfirmModal();
    };

    return (
        <div className="App">
            <div className={classes.root}>
                <header className="App-header">
                    <div className="header-text">mongorestore --drop --gzip --archive=/tmp/yourdump.gz</div>
                    <div className="header-text">docker exec -i e7825dd4664b mongorestore --drop --gzip --archive &lt; /tmp/yourdump.gz</div>
                </header>
                <List component="nav" aria-label="secondary mailbox folders" className="list">
                    {connections.map((value, index) => {
                        return (
                            <ListItem key={value.id} button>

                                <Grid container spacing={1} direction="row">
                                    <Grid item xs onClick={() => handleDump(value.id)}>
                                        <ListItemText>
                                            <Box fontFamily="Monospace" className="list-element">
                                                {value.name}
                                            </Box>
                                        </ListItemText>
                                    </Grid>

                                    <Grid container item xs={2} direction="row"
                                          justify="flex-end"
                                          alignItems="center" spacing={1}>
                                        <Grid item>
                                            <Button variant="contained" color="primary"
                                                    onClick={() => handleOpen(value)}>
                                                Edit
                                            </Button>
                                        </Grid>
                                        <Grid item>
                                            <Button variant="contained" color="secondary"
                                                    onClick={() => openDeleteModal(value)}>
                                                Delete
                                            </Button>
                                        </Grid>
                                    </Grid>
                                </Grid>
                            </ListItem>
                        )
                    })}
                </List>

                <Fab color="primary" aria-label="add" className={classes.fabButton}
                     onClick={() => handleOpen({name: '', connectionUrl: ''})}>
                    <AddIcon className="fab-add"/>
                </Fab>
            </div>

            <Modal
                aria-labelledby="simple-modal-title"
                aria-describedby="simple-modal-description"
                open={openEditModal}
                onClose={handleCloseEditModal}
            >
                <Fade in={openEditModal}>
                    <div style={modalStyle} className={classes.paper}>

                        <Grid container
                              direction="column"
                              justify="center"
                              alignItems="stretch"
                              spacing={2} className="edit-modal">
                            <Grid item>
                                <span>{editDto.id ? 'Update connection' : 'Create connection'}</span>
                            </Grid>
                            <Grid item container spacing={1} direction="column" justify="center"
                                  alignItems="stretch">
                                <Grid item>
                                    <TextField id="outlined-basic" label="Name" variant="outlined" fullWidth className="edit-modal-name"
                                               error={!valid} value={editDto.name} onChange={handleChangeName}/>
                                </Grid>
                                <Grid item>
                                    <TextField id="outlined-basic" label="Connection URL" variant="outlined" fullWidth className="edit-modal-connection-url"
                                               error={!valid} value={editDto.connectionUrl}
                                               onChange={handleChangeConnectionUrl}/>
                                </Grid>

                            </Grid>
                            <Grid item container spacing={1}>
                                <Grid item>
                                    <Button variant="contained" color="primary" disabled={!valid} className="edit-modal-save"
                                            onClick={() => onSave(editDto)}>
                                        Save
                                    </Button>
                                </Grid>
                                <Grid item>
                                    <Button variant="contained" color="secondary" onClick={handleCloseEditModal} className="edit-modal-cancel">
                                        Cancel
                                    </Button>
                                </Grid>
                            </Grid>
                        </Grid>
                    </div>
                </Fade>
            </Modal>

            <Modal
                aria-labelledby="simple-modal-title"
                aria-describedby="simple-modal-description"
                open={openConfirmModal}
                onClose={handleCloseConfirmModal}
            >
                <Fade in={openConfirmModal}>
                    <div style={modalStyle} className={classes.confirm}>

                        <Grid container
                              direction="column"
                              justify="center"
                              alignItems="stretch"
                              spacing={2}>
                            <Grid item>
                                Confirm delete {dbToDelete.name}?
                            </Grid>

                            <Grid item container spacing={1}>
                                <Grid item>
                                    <Button variant="contained" color="primary"
                                            onClick={() => handleDelete(dbToDelete.id)}>
                                        Yes
                                    </Button>
                                </Grid>
                                <Grid item>

                                    <Button variant="contained" color="secondary" onClick={handleCloseConfirmModal}>
                                        Cancel
                                    </Button>
                                </Grid>
                            </Grid>
                        </Grid>
                    </div>
                </Fade>
            </Modal>
        </div>
    );
}

export default (App);